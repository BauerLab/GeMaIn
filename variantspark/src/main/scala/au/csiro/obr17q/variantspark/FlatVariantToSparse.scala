package au.csiro.obr17q.variantspark

import au.csiro.obr17q.variantspark.CommonFunctions._
import org.apache.spark.mllib.clustering.KMeans
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark
import scala.io.Source
import org.apache.spark.sql.functions.max
import org.apache.spark.rdd.RDD
import au.csiro.obr17q.variantspark.model.SubjectSparseVariant


object FlatVariantToSparse extends SparkApp {
  conf.setAppName("VCF cluster")
  def main(args:Array[String]) {

    if (args.length < 1) {
        println("Usage: CsvClusterer <input-path>")
    }

    val inputFiles = args(0)
    val output = args(1) 
    val chunks = args(2).toInt
    val sqlContext = new org.apache.spark.sql.SQLContext(sc)
    import sqlContext.implicits._
    val flatVariandDF = sqlContext.parquetFile(inputFiles)    
    println(flatVariandDF.schema)
    
    
    val maxVariantIndex = flatVariandDF.agg(max(flatVariandDF("variantIndex")))
      .collectAsList().get(0).getInt(0) //flatVariandDF.rdd.map(r => r.getInt(1)).max();
    
    println(maxVariantIndex)
    val parirRDD:RDD[(String,(Int,Double))] = flatVariandDF.rdd.map(r => (r.getString(0), (r.getInt(1), r.getDouble(2))))
    parirRDD
      .groupByKey(chunks)
      .mapValues(i => Vectors.sparse(maxVariantIndex + 1, i.to[Seq]).toSparse)
      .map {case (subjectId, sparseVector) =>
        SubjectSparseVariant(subjectId, sparseVector.size, sparseVector.indices, sparseVector.values)}
      .toDF().saveAsParquetFile(output)  
  } 
}