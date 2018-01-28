# Skraper

 [ ![Download](https://api.bintray.com/packages/bruno-ortiz/maven/Skraper/images/download.svg) ](https://bintray.com/bruno-ortiz/maven/Skraper/_latestVersion)

# About

Skraper is a simple crawler written in Kotlin, it is somehow based in Scrapy.

# Gradle
 
Skraper is published through Jcenter. Using Skraper is as simple as:

```groovy
repositories {
    jcenter()
}

dependencies {
    compile 'br.com.skraper:skraper:<latest-version>'
}
```

# Usage

Using Skraper is really simple, you only need to implement 'Crawler' interface, and user CrawlerExecutor to start crawling.

```kotlin
object SimpleCrawler : Crawler {
    override val name = "My crawler name"

    override fun parse(doc: Document) = buildSequence {
        val crawlingResult = CrawlingItem(doc.select("#someTag > h1").text())

        yield(crawlingResult)
    }
}

fun main(args: Array<String>) {
    CrawlerExecutor().start("http://www.someURL.com/start", SimpleCrawler)
}

```

## Queuing new pages

When parsing a page the parse method returns a sequence generator, that can yield new values of 2 types:

* CrawlingItem -> It means that some meaningful data was extract from this page and must be processed(logged, saved in the DB...etc)
* NextPage -> It means that a different page will be put into the queue to be downloaded, parsed and processed.

```kotlin
object SimplePageCrawler : Crawler {
    override val name = "My page crawler name"

    override fun parse(doc: Document) = buildSequence {
        for (el in doc.select("ul > li > a")) {
            yield(NextPage(el.attr("href"), SomeOtherCrawler))
        }
    }
}
```

When a NextPage is yielded, we must tell which Crawler will be used to parse this page.

# Item processors

When a CrawlingItem is added to the queue, the executor will try to find a suitable processor to handle that item.

A processor is a simple ActorJob<T> that has a open channel for receiving items. Example:

```kotlin
data class SomeVO(val itemName:String)

object SomeVOCrawler : Crawler {
    override val name = "My crawler name"

    override fun parse(doc: Document) = buildSequence {
        val someVO = SomeVO(doc.select("#someTag > h1").text())
        val crawlingResult = CrawlingItem(someVO)

        yield(crawlingResult)
    }
}

fun main(args: Array<String>) {
    CrawlerExecutor().apply{
        registerProcessor(SomeVO::class.java, someVOProcessor())
    }.start("http://www.someURL.com/start", SomeVOCrawler)
}


fun someVOProcessor() = actor<SomeVO> {
    var counter = 0
    channel.consumeEach { vo ->
        counter++
        println("Received item $vo")
    }
    println("Yay, crawling ended, received $counter objects.")
}
```


# TODOS

* Support JSON crawling
* Customize http client(add proxy support)
* Add some delay between downloads
