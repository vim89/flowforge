```scala
implicit class TableDataframeActions(dataframe: DataFrame) extends Serializable {

    /**
     * [[Table.TableDataframeActions#getAffectedPartitionDates(java.time.LocalDateTime, java.lang.String)]]
     *
     * @param since     previous DateTime when this function was accessed or list all partitions that got impacted after (since) this DateTime
     * @param dateRegex By default yyyy-MM-dd; regex = \\d{4}-\\d{2}-\\d{2}, pass explicitly if other than default pattern
     * @return Returns list of date partitions of given dataframe that got affected after given since date
     *         Usage:
     *         val affectedDates = spark
     *         .sql("SELECT * FROM ww_chnl_perf_app.chnl_perf_item_fact_dly WHERE op_cmpny_cd = 'WMT-US'")
     *         .getAffectedPartitionDates(since = yearMonthDate24HrTs"2022-04-03 00:00:00")
     * https://raw.githubusercontent.com/vim89/reference-utilities/refs/heads/main/src/main/scala/com/vim/de/utils/spark/Table.scala
     */
    def getAffectedPartitionDates(
        since: LocalDateTime,
        dateRegex: String = "\\d{4}-\\d{2}-\\d{2}"
    ): Array[(LocalDate, LocalDateTime)] = {
      val FILE_LOCATION = "file_location"
      val affectedDates = dataframe
        .select(input_file_name().alias(FILE_LOCATION))
        .distinct()
        .rdd
        .mapPartitions(partition =>
          partition.flatMap { field =>
            val location        = field.getAs[String]("file_location")
            val blobLocation    = blob"$location"
            val blobLastUpdated = blobLocation.updatedTs(None)
            if (blobLastUpdated.isAfter(since)) {
              Some((extractDate(location, dateRegex), blobLastUpdated))
            } else {
              None
            }
          }
        )
        .reduceByKey((v1, v2) => if (v1.isAfter(v2)) v1 else v2)
        .flatMap(t => t._1.map((_, t._2)))
        .distinct()
        .collect()
      affectedDates
    }
  }
```

`blob"$location"` in above code - `blob` is a string interpolator which converts GCS blob URI to a GCS Blob type in scala.
```scala
/** [[GcsInterpolators.GcsInterpolator#blob(scala.collection.Seq)]]
     * Fetches
     *
     * @param args GCS URI given as plain string or simple interpolated string
     * @return Blob type object
     * https://raw.githubusercontent.com/vim89/reference-utilities/refs/heads/main/src/main/scala/com/vim/de/utils/gcp/GcsInterpolators.scala
     */
def blob(args: Any*): Blob = {
      val totalString = sc.s(args: _*)
      val gcsObject   = totalString.toGcsObject
      if (gcsObject.objectName.isEmpty) {
        throw new IllegalArgumentException(
          """Path of object is empty. To fetch only bucket object use bucket"" GcsInterpolator""".stripMargin
        )
      }
      val tryResult = trySafely(
        unsafeCodeBlock = STORAGE_SERVICE.get(
          gcsObject.bucketName,
          gcsObject.objectName.get,
          BlobGetOption.fields(Storage.BlobField.values(): _*)
        ),
        errorMessage = Some(s"Error fetching blob object $args")
      )
      tryResult.merge
    }
```

## How affected partitions should work -

### History data loads or first time loads - 
1. We do not have any `since` for a target table - may be in audit database also there is no entries of this target table.
2. So We must load all partitions from source so return all date partitions of target table

### Subsequent loads
1. We fetch last successful load time from audit and feed to function
2. Now we check in GCS which partitions (folders / directories of Google cloud storage) have their last-modified timestamp are greater than `since`
3. Return only those for performing table scans

### Current prototype implementations
1. We used apache spark for achieving speed, and it's parallelism in above prototype implementation
2. You will find many other variants without spark as well in https://raw.githubusercontent.com/vim89/reference-utilities/refs/heads/main/src/main/scala/com/vim/de/utils/spark/Table.scala
Let's 
