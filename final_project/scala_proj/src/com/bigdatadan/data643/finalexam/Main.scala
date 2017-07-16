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

object Main {
  
  val DEBUG_ALL = true
  
  val spark = org.apache.spark.sql.SparkSession.builder.master("local").appName("Spark CSV Reader").getOrCreate;
  
  def runForMusicTag(genres:List[String]) {
    val artists = spark.read.format("com.databricks.spark.csv").option("header", "true").option("inferSchema", "true").option("delimiter", "\t").load("../data/artists.dat")
    // the column renaming is NOT WORKING
    //val artists = spark.read.format("com.databricks.spark.csv").option("header", "true").option("inferSchema", "true").option("delimiter", "|").load("../data/artists.dat").withColumnRenamed("id", "artistID").withColumnRenamed("name", "artistName")
    val tags = spark.read.format("com.databricks.spark.csv").option("header", "true").option("inferSchema", "true").option("delimiter", "\t").load("../data/tags.dat")
    val user_artists = spark.read.format("com.databricks.spark.csv").option("inferSchema", "true").option("header", "true").option("delimiter", "\t").load("../data/user_artists.dat")
    val user_friends = spark.read.format("com.databricks.spark.csv").option("inferSchema", "true").option("header", "true").option("delimiter", "\t").load("../data/user_friends.dat")
    val user_tagged_artists = spark.read.format("com.databricks.spark.csv").option("inferSchema", "true").option("header", "true").option("delimiter", "\t").load("../data/user_taggedartists.dat")

    if(DEBUG_ALL){
      artists.show()
      tags.show()
      user_artists.show()
      user_friends.show()
      user_tagged_artists.show()
    }
    
    artists.createOrReplaceTempView("artists")
    tags.createOrReplaceTempView("tags")
    user_artists.createOrReplaceTempView("user_artists")
    user_friends.createOrReplaceTempView("user_friends")
    user_tagged_artists.createOrReplaceTempView("user_tagged_artists")
    
    val textBuf = new StringBuilder
    val htmlBuf = new StringBuilder
    htmlBuf.append("<HTML>" + "\n")
    htmlBuf.append("<HEAD><link rel='stylesheet' type='text/css' href='SUGGESTIONS.css'></HEAD>" + "\n")
    htmlBuf.append("<BODY>" + "\n")
    htmlBuf.append("<H1>ARTIST SUGGESTIONS BY GENRE</H1>" + "\n")
    for (tagValue <- genres){
      println("Working on GENRE (TAG): " + tagValue)
      // the commented out one has the counts...but get errors:
      //val data = spark.sql("select uta.userID, user_artists.artistID, artists.name, avg(user_artists.weight) as userArtistWeight, uta.tagID, tagValue, (SELECT COUNT(*) AS NUM_FRIENDS_WHO_LISTEN FROM user_friends WHERE user_friends.userID = uta.userID AND user_friends.friendID in (select distinct(userID) from user_artists where user_artists.artistID = uta.artistID)) AS FRIEND_COUNT from artists, tags, user_tagged_artists uta, user_artists where artists.id = uta.artistID and tags.tagID = uta.tagID and uta.userId = user_artists.userID and uta.artistID = user_artists.artistID group by uta.userID, user_artists.artistID, name, uta.tagID, tagValue")
  
      // Filter by tag (or genre)
      
      //val data = spark.sql("select uta.userID, user_artists.artistID, artists.name, avg(user_artists.weight) as userArtistWeight, uta.tagID, tagValue from artists, tags, user_tagged_artists uta, user_artists where artists.id = uta.artistID and tags.tagID = uta.tagID and uta.userId = user_artists.userID and uta.artistID = user_artists.artistID group by uta.userID, user_artists.artistID, name, uta.tagID, tagValue")
      val data = spark.sql("select uta.userID, user_artists.artistID, artists.name, avg(user_artists.weight) as userArtistWeight, uta.tagID, tagValue from artists, tags, user_tagged_artists uta, user_artists where artists.id = uta.artistID and tags.tagID = uta.tagID and uta.userId = user_artists.userID and uta.artistID = user_artists.artistID and tagValue = '" + tagValue + "' group by uta.userID, user_artists.artistID, name, uta.tagID, tagValue")
      
      // now get the ratings on there...
      if(DEBUG_ALL){ 
        data.show()
      }
  
      data.createOrReplaceTempView("data")
  
      val tagSummary = spark.sql("select tagID, tagValue, count(*) as the_count from data group by tagID, tagValue order by the_count desc")
      if(DEBUG_ALL){ 
        tagSummary.show()
      }
  
      tagSummary.createOrReplaceTempView("tagSummary")
  
      val userItemRatingDF = spark.sql("select userID, artistID, userArtistWeight from data")
      val Array(train, test) = userItemRatingDF.randomSplit(Array(0.8, 0.2))    //    
      
      // https://github.com/matthewkalan/mongo-spark-recommender-example/blob/master/src/ALSExampleMongoDB.scala
      // Build the recommendation model using ALS on the training data
      val als = new ALS().setMaxIter(5).setRegParam(0.01).setUserCol("userID").setItemCol("artistID").setRatingCol("userArtistWeight")
      if(DEBUG_ALL){
        train.show()
      }
      val model = als.fit(train)      //train the model
      // Evaluate the model by computing the RMSE on the test data
      val predictions = model.transform(test)
        .withColumn("rating", col("userArtistWeight").cast(DoubleType))
        .withColumn("prediction", col("prediction").cast(DoubleType))
      
      if(DEBUG_ALL){
        predictions.show()  
      }
        
      //val recommendations = predictions.collect()./*sortBy(-_.rating).*/take(50)
      val recommendations = predictions.sort(desc("userArtistWeight")).collect().take(50)
      
      var suggestions = new ListBuffer[String]()
      var i = 1
      recommendations.foreach { r =>
        val suggestedArtist = spark.sql("select distinct(name) as theName from artists where id = " + r.get(1)).first()
        //println("%2d".format(i) + ": " + r + " *** " + suggestedArtist)
        suggestions += suggestedArtist.get(0).toString()
        //suggestions += ("%2d".format(i) + ": " + suggestedArtist.get(0) + "\n")
        i += 1
      }
      
      textBuf.append("------------------------------------------------" + "\n")
      textBuf.append("For Tag = [" + tagValue + "], We Suggest: " + "\n")
      textBuf.append("------------------------------------------------" + "\n")
      var j=1
      suggestions.foreach { a =>
        textBuf.append(j + ")" + a + "\n")
        j += 1
      }
      suggestions.foreach(textBuf.append)
      
      htmlBuf.append("<DIV>" + "\n")
      htmlBuf.append("<H2>" + tagValue + "</H2>" + "\n")
      htmlBuf.append("<OL>" + "\n")
      suggestions.foreach { a =>
        htmlBuf.append("<LI>")
        htmlBuf.append("<a href='https://www.youtube.com/results?search_query=" + a + "' target='_blank'><IMG alt='YouTube' SRC='img/youtube.jpeg'></a>")
        htmlBuf.append("<a href='http://www.musixhub.com/search.php?name=" + a + "' target='_blank'><IMG alt='MusixHub' SRC='img/musixhub.png'></a>")
        htmlBuf.append("<a href='https://twitter.com/search?q=" + a + "' target='_blank'><IMG alt='Twitter' SRC='img/twitter.jpg'></a>")
        htmlBuf.append(a + "</LI>" + "\n")
      }
      htmlBuf.append("</OL>" + "\n")
      htmlBuf.append("</DIV>" + "\n")
    }
    htmlBuf.append("</BODY>" + "\n")
    htmlBuf.append("</HTML>" + "\n")
    val pw = new PrintWriter(new File("SUGGESTIONS.html"))
    pw.write(htmlBuf.toString)
    pw.close

    println(textBuf.toString())
  }

  def main(args: Array[String]): Unit = {
  	println("DATA643 Final Project:");
  	
  	//runForMusicTag(List("rock","pop","alternative","electronic","indie","female vocalists","80s","dance","alternative rock","classic rock","british","indie rock","singer-songwriter","hard rock","experimental","metal","ambient","90s","new wave","seen live"))
  	runForMusicTag(List("singer-songwriter", "chillout"))
  }  

}