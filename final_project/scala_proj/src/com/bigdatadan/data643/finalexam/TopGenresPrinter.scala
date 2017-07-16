package com.bigdatadan.data643.finalexam

import org.apache.spark.sql.SparkSession
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.DoubleType
// https://www.quora.com/Why-are-there-two-ML-implementations-in-Spark-ML-and-MLlib-and-what-are-their-different-features
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.recommendation.ALS
import scala.collection.mutable.ListBuffer
import java.io._

object TopGenresPrinter {
  
  val spark = org.apache.spark.sql.SparkSession.builder.master("local").appName("Spark CSV Reader").getOrCreate;
  
  def main(args: Array[String]): Unit = {
    val tags = spark.read.format("com.databricks.spark.csv").option("header", "true").option("inferSchema", "true").option("delimiter", "\t").load("../data/tags.dat")
    val user_tagged_artists = spark.read.format("com.databricks.spark.csv").option("inferSchema", "true").option("header", "true").option("delimiter", "\t").load("../data/user_taggedartists.dat")
    tags.createOrReplaceTempView("tags")
    user_tagged_artists.createOrReplaceTempView("user_tagged_artists")
    spark.sql("select tagValue, count(*) from tags, user_tagged_artists uta where tags.tagID = uta.tagID group by tagValue order by count(*) desc").show()
  } 

}