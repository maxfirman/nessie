# Nessie Spark SQL Extensions
Spark SQL extensions provide an easy way to execute common Nessie commands via SQL.

## How to use them

{%- for sparkver in [
  '3.3',
  '3.2',
  '3.1',
] %}

### Spark {{sparkver}}

In order to be able to use Nessie's custom Spark SQL extensions with Spark {{sparkver}}.x, one needs to configure
`{{ iceberg_spark_runtime(sparkver).spark_jar_package }}` along with `{{ nessie_spark_extensions(sparkver).spark_jar_package }}`

Here's an example of how this is done when starting the `spark-sql` shell:

``` sh
bin/spark-sql 
  --packages "{{ iceberg_spark_runtime(sparkver).spark_jar_package }},{{ nessie_spark_extensions(sparkver).spark_jar_package }}"
  --conf spark.sql.extensions="org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions,org.projectnessie.spark.extensions.NessieSparkSessionExtensions"
  --conf <other settings>
```
{%- endfor %}

Additional configuration details can be found in the [Spark via Iceberg](iceberg/spark.md) docs.

## Grammar
The current grammar is shown below:
```
: CREATE (BRANCH|TAG) (IF NOT EXISTS)? reference=identifier (IN catalog=identifier)? (FROM fromRef=identifier)?
| DROP (BRANCH|TAG) (IF EXISTS)? identifier (IN catalog=identifier)?
| USE REFERENCE reference=identifier (AT tsOrHash=identifier)?  (IN catalog=identifier)?
| LIST REFERENCES (IN catalog=identifier)?
| SHOW REFERENCE (IN catalog=identifier)?
| MERGE BRANCH (reference=identifier)? (INTO toRef=identifier)?  (IN catalog=identifier)?
| SHOW LOG (reference=identifier)? (IN catalog=identifier)?
| ASSIGN (BRANCH|TAG) (reference=identifier)? (TO toRef=identifier)? (IN catalog=identifier)?
```

## Creating Branches/Tags

Creating a branch `dev` in the `nessie` catalog (in case it doesn't already exist):

* `CREATE BRANCH IF NOT EXISTS dev IN nessie`

Creating a tag `devTag` in the `nessie` catalog (in case it doesn't already exist):

* `CREATE TAG IF NOT EXISTS devTag IN nessie`

Creating a branch `dev` in the `nessie` catalog off of an existing branch/tag `base`:

* `CREATE BRANCH IF NOT EXISTS dev IN nessie FROM base`

Note that in case `base` doesn't exist, Nessie will fall back to the default branch (`main`).

## Dropping Branches/Tags

Dropping a branch `dev` in the `nessie` catalog (in case it exists):

* `DROP BRANCH IF EXISTS dev IN nessie`

Dropping a tag `devTag` in the `nessie` catalog (in case it exists):

* `DROP TAG IF EXISTS devTag IN nessie`

Note: the `IF EXISTS` clause is optional and is only supported for Nessie SQL Extensions 0.72 or 
higher.

## Switching to a Branch/Tag

In order to switch to the HEAD of the branch/tag `ref` in the `nessie` catalog:

* `USE REFERENCE ref IN nessie`

It is also possible to switch to a specific timestamp on a given branch/tag:

* ``USE REFERENCE ref AT `2021-10-06T08:50:37.157602` IN nessie``

Additionally, one can switch to a specific hash on a given branch/tag:

* `USE REFERENCE ref AT dd8d46a3dd5478ce69749a5455dba29d74f6d1171188f4c21d0e15ff4a0a9a9b IN nessie`

## Listing available Branches/Tags

One can list available branches/tags in the `nessie` catalog via:

* `LIST REFERENCES IN nessie`

## Showing details of the current Branch/Tag

One can see details about the current branch/tag in the `nessie` catalog via:

* `SHOW REFERENCE IN nessie`

## Showing the Commit Log of the current Branch/Tag

It is possible to look at the commit log of a particular branch/tag in the `nessie` catalog via:

* `SHOW LOG dev IN nessie`

## Assigning Branches/Tags

Assigning a branch `dev` to `base` in catalog `nessie` can be done via:

* `ASSIGN BRANCH dev TO base IN nessie`

Assigning a tag `devTag` to `base` in catalog `nessie` can be done via:

* `ASSIGN TAG devTag TO base IN nessie`

Note that in case `base` doesn't exist, Nessie will fall back to the default branch (`main`).

It is also possible to assign a branch/tag to a `base` at a particular `hash`:

* `ASSIGN TAG devTag TO base AT dd8d46a3dd5478ce69749a5455dba29d74f6d1171188f4c21d0e15ff4a0a9a9b IN nessie`


## Merging a Branch into another Branch

Merging branch `dev` into `base` for the `nessie` catalog can be done via:

* `MERGE BRANCH dev INTO base IN nessie`

Note that in case `base` doesn't exist, Nessie will fall back to the default branch (`main`).

## Usage with Iceberg CALL procedures

!!! note
    For Iceberg version >= 0.14.0 with spark versions <= 3.1
    or Iceberg version < 0.14.0 with any of the spark versions,
    1. Compaction always works on initial catalog configurations (like `spark.sql.catalog._catalogName_.ref`)
    and ignores the reference set using `USE REFERENCE` command.
    So, need to have a new spark session with required reference name configuration to apply the compaction for tables in non-default references.
    2. For compaction, `TableReference` syntax is not supported as table identifier.
