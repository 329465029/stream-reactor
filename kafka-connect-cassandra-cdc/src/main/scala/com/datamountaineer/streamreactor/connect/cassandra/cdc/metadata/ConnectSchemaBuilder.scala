/*
 * Copyright 2017 Datamountaineer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datamountaineer.streamreactor.connect.cassandra.cdc.metadata


import com.datamountaineer.streamreactor.connect.cassandra.cdc.config.CdcConfig
import org.apache.cassandra.config.{CFMetaData, ColumnDefinition}
import org.apache.cassandra.db.marshal._
import org.apache.kafka.connect.data._

import scala.collection.JavaConversions._

object ConnectSchemaBuilder {
  def apply(metadata: CFMetaData)(implicit config: CdcConfig): Schema = {
    import org.apache.kafka.connect.data.SchemaBuilder
    val builder: SchemaBuilder = SchemaBuilder.struct.name(metadata.cfName)
    metadata.allColumns().foreach { cd =>
      addField(cd, builder)
    }
    builder.build()
  }

  private def addField(cd: ColumnDefinition, builder: SchemaBuilder)(implicit config: CdcConfig): Unit = {
    val fieldName = cd.name.toString

    val schema = fromType(cd.`type`)
    builder.field(fieldName, schema)
  }

  def fromType(`type`: AbstractType[_])(implicit config: CdcConfig): Schema = {
    `type` match {
      case _: AsciiType => Schema.OPTIONAL_STRING_SCHEMA
      case _: LongType => Schema.OPTIONAL_INT64_SCHEMA
      case _: BytesType => Schema.OPTIONAL_BYTES_SCHEMA
      case _: BooleanType => Schema.OPTIONAL_BOOLEAN_SCHEMA
      case _: CounterColumnType => Schema.OPTIONAL_INT64_SCHEMA
      case _: SimpleDateType => Date.builder().optional().build()
      case _: DecimalType =>
        //we don't have information about the decimal scale
        Decimal.builder(config.decimalScale).optional().build()
      case _: DoubleType => Schema.OPTIONAL_FLOAT64_SCHEMA
      case _: DurationType =>
        //we will store it as string!
        Schema.OPTIONAL_STRING_SCHEMA
      case _: EmptyType => Schema.OPTIONAL_STRING_SCHEMA
      case _: FloatType => Schema.OPTIONAL_FLOAT32_SCHEMA
      case _: InetAddressType =>
        //we store it as String
        Schema.OPTIONAL_STRING_SCHEMA
      case _: Int32Type => Schema.OPTIONAL_INT32_SCHEMA
      case _: ShortType => Schema.OPTIONAL_INT16_SCHEMA
      case _: UTF8Type => Schema.OPTIONAL_STRING_SCHEMA
      case _: TimeType => Time.builder().optional().build()
      case _: TimestampType => Timestamp.builder().optional().build()
      case _: TimeUUIDType =>
        //we store the UUID as string
        Schema.OPTIONAL_STRING_SCHEMA
      case _: ByteType => Schema.OPTIONAL_INT8_SCHEMA
      case _: UUIDType =>
        //we store the UUID as string
        Schema.OPTIONAL_STRING_SCHEMA
      case _: IntegerType => Schema.OPTIONAL_INT32_SCHEMA

      case l: ListType[_] =>
        val tpe = l.getElementsType
        val schema = fromType(tpe)
        SchemaBuilder.array(schema).optional().build()

      case m: MapType[_, _] =>
        val keyType = m.getKeysType
        val keySchema = fromType(keyType)

        val valueType = m.getValuesType
        val valueSchema = fromType(valueType)
        SchemaBuilder.map(keySchema, valueSchema).optional().build()

      case s: SetType[_] =>
        val tpe = s.getElementsType
        val schema = fromType(tpe)
        SchemaBuilder.array(schema).optional().build()

      case ut: UserType =>
        val sb = SchemaBuilder.struct()
          .optional()
          .name(ut.getNameAsString)

        (0 until ut.fieldNames().size).map { i =>
          val fn = ut.fieldNameAsString(i)
          val tpe = ut.fieldType(i)
          sb.field(fn, fromType(tpe))
        }
        sb.build()

      case other => throw new IllegalArgumentException(s"CQL type:${other.asCQL3Type().toString} is not supported")

    }

  }

  def coerceValue(value: Any, `type`: AbstractType[_], schema: Schema)(implicit config: CdcConfig): Any = {
    `type` match {
      case _: DurationType | _: InetAddressType | _: TimeUUIDType | _: UUIDType => Option(value).map(_.toString).orNull
      case _: EmptyType => null
      case _: TimeType => Option(value).map(_.asInstanceOf[Long].toInt).orNull
      case _: TimestampType => Option(value).map(_.asInstanceOf[java.util.Date]).map(Timestamp.fromLogical(schema, _)).orNull
      case _: DecimalType =>
        Option(value).map { d =>
          Decimal.fromLogical(schema, d.asInstanceOf[BigDecimal].bigDecimal.setScale(config.decimalScale))
        }.orNull
      case other => value
    }

  }

}
