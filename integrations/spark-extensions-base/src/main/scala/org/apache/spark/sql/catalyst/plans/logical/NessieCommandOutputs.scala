/*
 * Copyright (C) 2022 Dremio
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
package org.apache.spark.sql.catalyst.plans.logical

import org.apache.spark.sql.catalyst.expressions.AttributeReference
import org.apache.spark.sql.types.{DataTypes, Metadata, StructField, StructType}

object NessieCommandOutputs {
  def referenceOutput(): Seq[AttributeReference] = new StructType(
    Array[StructField](
      StructField(
        "refType",
        DataTypes.StringType,
        nullable = false,
        Metadata.empty
      ),
      StructField(
        "name",
        DataTypes.StringType,
        nullable = false,
        Metadata.empty
      ),
      StructField(
        "hash",
        DataTypes.StringType,
        nullable = false,
        Metadata.empty
      )
    )
  ).toAttributes

  def simpleReferenceOutput(): Seq[AttributeReference] = new StructType(
    Array[StructField](
      StructField(
        "name",
        DataTypes.StringType,
        nullable = false,
        Metadata.empty
      ),
      StructField(
        "hash",
        DataTypes.StringType,
        nullable = false,
        Metadata.empty
      )
    )
  ).toAttributes

  def dropReferenceOutput(): Seq[AttributeReference] = new StructType(
    Array[StructField](
      StructField(
        "status",
        DataTypes.StringType,
        nullable = false,
        Metadata.empty
      )
    )
  ).toAttributes

  def showLogOutput(): Seq[AttributeReference] = new StructType(
    Array[StructField](
      StructField(
        "author",
        DataTypes.StringType,
        nullable = false,
        Metadata.empty
      ),
      StructField(
        "committer",
        DataTypes.StringType,
        nullable = false,
        Metadata.empty
      ),
      StructField(
        "hash",
        DataTypes.StringType,
        nullable = false,
        Metadata.empty
      ),
      StructField(
        "message",
        DataTypes.StringType,
        nullable = false,
        Metadata.empty
      ),
      StructField(
        "signedOffBy",
        DataTypes.StringType,
        nullable = false,
        Metadata.empty
      ),
      StructField(
        "authorTime",
        DataTypes.TimestampType,
        nullable = false,
        Metadata.empty
      ),
      StructField(
        "committerTime",
        DataTypes.TimestampType,
        nullable = false,
        Metadata.empty
      ),
      StructField(
        "properties",
        DataTypes
          .createMapType(DataTypes.StringType, DataTypes.StringType, false),
        nullable = false,
        Metadata.empty
      )
    )
  ).toAttributes
}
