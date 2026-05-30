package org.gnit.lucenekmp.analysis.charfilter

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.StringWriter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ClasspathResourceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestHTMLStripCharFilter : BaseTokenStreamTestCase() {
    // this is some text  here is a  link  and another  link . This is an entity: & plus a <.  Here is
    // an &
    //
    @Test
    @Throws(Exception::class)
    fun test() {
        val html =
            "<div class=\"foo\">this is some text</div> here is a <a href=\"#bar\">link</a> and " +
                "another <a href=\"http://lucene.apache.org/\">link</a>. " +
                "This is an entity: &amp; plus a &lt;.  Here is an &. <!-- is a comment -->"
        val gold =
            "\nthis is some text\n here is a link and " +
                "another link. " +
                "This is an entity: & plus a <.  Here is an &. "
        assertHTMLStripsTo(html, gold, null)
    }

    // Some sanity checks, but not a full-fledged check
    @Test
    @Throws(Exception::class)
    fun testHTML() {
        val stream = ClasspathResourceLoader(this::class).openResource("htmlStripReaderTest.html")
        val reader = HTMLStripCharFilter(InputStreamReader(stream, StandardCharsets.UTF_8))
        val builder = StringBuilder()
        var ch = -1
        while (reader.read().also { ch = it } != -1) {
            builder.append(ch.toChar())
        }
        val str = builder.toString()
        assertTrue(str.indexOf("&lt;") == -1, "Entity not properly escaped") // there is one > in the text
        assertTrue(
            str.indexOf("forrest") == -1 && str.indexOf("Forrest") == -1,
            "Forrest should have been stripped out"
        )
        assertTrue(str.trim().startsWith("Welcome to Solr"), "File should start with 'Welcome to Solr' after trimming")

        assertTrue(str.trim().endsWith("Foundation."), "File should start with 'Foundation.' after trimming")
    }

    @Test
    @Throws(Exception::class)
    fun testMSWord14GeneratedHTML() {
        val stream = ClasspathResourceLoader(this::class).openResource("MS-Word 14 generated.htm")
        val reader = HTMLStripCharFilter(InputStreamReader(stream, StandardCharsets.UTF_8))
        val gold = "This is a test"
        val builder = StringBuilder()
        var ch = 0
        while (reader.read().also { ch = it } != -1) {
            builder.append(ch.toChar())
        }
        // Compare trim()'d output to gold
        assertEquals(
            gold,
            builder.toString().trim(),
            "'${builder.toString().trim()}' is not equal to '$gold'"
        )
    }

    @Test
    @Throws(Exception::class)
    fun testGamma() {
        assertHTMLStripsTo("&Gamma;", "\u0393", mutableSetOf("reserved"))
    }

    @Test
    @Throws(Exception::class)
    fun testEntities() {
        val test = "&nbsp; &lt;foo&gt; &Uuml;bermensch &#61; &Gamma; bar &#x393;"
        val gold = "  <foo> \u00DCbermensch = \u0393 bar \u0393"
        assertHTMLStripsTo(test, gold, mutableSetOf("reserved"))
    }

    @Test
    @Throws(Exception::class)
    fun testMoreEntities() {
        val test = "&nbsp; &lt;junk/&gt; &nbsp; &#33; &#64; and &#8217;"
        val gold = "  <junk/>   ! @ and ’"
        assertHTMLStripsTo(test, gold, mutableSetOf("reserved"))
    }

    @Test
    @Throws(Exception::class)
    fun testReserved() {
        val test = "aaa bbb <reserved ccc=\"ddddd\"> eeee </reserved> ffff <reserved ggg=\"hhhh\"/> <other/>"
        val set = mutableSetOf<String>()
        set.add("reserved")
        val reader: Reader = HTMLStripCharFilter(StringReader(test), set)
        val builder = StringBuilder()
        var ch = 0
        while (reader.read().also { ch = it } != -1) {
            builder.append(ch.toChar())
        }
        val result = builder.toString()
        // System.out.println("Result: " + result);
        assertTrue(result.indexOf("reserved") == 9, "Escaped tag not preserved: ${result.indexOf("reserved")}")
        assertTrue(
            result.indexOf("reserved", 15) == 38,
            "Escaped tag not preserved: ${result.indexOf("reserved", 15)}"
        )
        assertTrue(
            result.indexOf("reserved", 41) == 54,
            "Escaped tag not preserved: ${result.indexOf("reserved", 41)}"
        )
        assertTrue(result.indexOf("other") == -1, "Other tag should be removed")
    }

    @Test
    @Throws(Exception::class)
    fun testMalformedHTML() {
        val testGold =
            arrayOf(
                "a <a hr<ef=aa<a>> </close</a>",
                "a <a hr<ef=aa> </close",
                "<a href=http://dmoz.org/cgi-bin/add.cgi?where=/arts/\" class=lu style=\"font-size: 9px\" target=dmoz>Submit a Site</a>",
                "Submit a Site",
                "<a href=javascript:ioSwitch('p8','http://www.csmonitor.com/') title=expand id=e8 class=expanded rel=http://www.csmonitor.com/>Christian Science",
                "Christian Science",
                "<link rel=\"alternate\" type=\"application/rss+xml\" title=\"San Francisco \" 2008 RSS Feed\" href=\"http://2008.sf.wordcamp.org/feed/\" />",
                "\n",
                // "<" before ">" inhibits tag recognition
                "<a href=\" http://www.surgery4was.happyhost.org/video-of-arthroscopic-knee-surgery symptoms.html, heat congestive heart failure <a href=\" http://www.symptoms1bad.happyhost.org/canine",
                "<a href=\" http://www.surgery4was.happyhost.org/video-of-arthroscopic-knee-surgery symptoms.html, heat congestive heart failure <a href=\" http://www.symptoms1bad.happyhost.org/canine",
                "<a href=\"http://ucblibraries.colorado.edu/how/index.htm\"class=\"pageNavAreaText\">",
                "",
                "<link title=\"^\\\" 21Sta's Blog\" rel=\"search\"  type=\"application/opensearchdescription+xml\"  href=\"http://21sta.com/blog/inc/opensearch.php\" />",
                "\n",
                "<a href=\"#postcomment\" title=\"\"Leave a comment\";\">?",
                "?",
                "<a href='/modern-furniture'   ' id='21txt' class='offtab'   onMouseout=\"this.className='offtab';  return true;\" onMouseover=\"this.className='ontab';  return true;\">",
                "",
                "<a href='http://alievi.wordpress.com/category/01-todos-posts/' style='font-size: 275%; padding: 1px; margin: 1px;' title='01 - Todos Post's (83)'>",
                "",
                "The <a href=<a href=\"http://www.advancedmd.com>medical\">http://www.advancedmd.com>medical</a> practice software</a>",
                "The <a href=http://www.advancedmd.com>medical practice software",
                "<a href=\"node/21426\" class=\"clipTitle2\" title=\"Levi.com/BMX 2008 Clip of the Week 29 \"Morgan Wade Leftover Clips\"\">Levi.com/BMX 2008 Clip of the Week 29...",
                "Levi.com/BMX 2008 Clip of the Week 29...",
                "<a href=\"printer_friendly.php?branch=&year=&submit=go&screen=\";\">Printer Friendly",
                "Printer Friendly",
                "<a href=#\" ondragstart=\"return false\" onclick=\"window.external.AddFavorite('http://www.amazingtextures.com', 'Amazing Textures');return false\" onmouseover=\"window.status='Add to Favorites';return true\">Add to Favorites",
                "Add to Favorites",
                "<a href=\"../at_home/at_home_search.html\"../_home/at_home_search.html\">At",
                "At",
                "E-mail: <a href=\"\"mailto:XXXXXX@example.com\" \">XXXXXX@example.com </a>",
                "E-mail: XXXXXX@example.com ",
                "<li class=\"farsi\"><a title=\"A'13?\" alt=\"A'13?\" href=\"http://www.america.gov/persian\" alt=\"\" name=\"A'13?\"A'13? title=\"A'13?\">A'13?</a></li>",
                "\nA'13?\n",
                "<li><a href=\"#28\" title=\"Hubert \"Geese\" Ausby\">Hubert \"Geese\" Ausby</a></li>",
                "\nHubert \"Geese\" Ausby\n",
                "<href=\"http://anbportal.com/mms/login.asp\">",
                "\n",
                "<a href=\"",
                "<a href=\"",
                "<a href=\">",
                "",
                "<a rel=\"nofollow\" href=\"http://anissanina31.skyrock.com/1895039493-Hi-tout-le-monde.html\" title=\" Hi, tout le monde !>#</a>",
                "#",
                "<a href=\"http://annunciharleydavidsonusate.myblog.it/\" title=\"Annunci Moto e Accessori Harley Davidson\" target=\"_blank\"><img src=\"http://annunciharleydavidsonusate.myblog.it/images/Antipixel.gif\" /></a>",
                "",
                "<a href=\"video/addvideo&v=120838887181\" onClick=\"return confirm('Are you sure you want  add this video to your profile? If it exists some video in your profile will be overlapped by this video!!')\" \" onmouseover=\"this.className='border2'\" onmouseout=\"this.className=''\">",
                "",
                "<a href=#Services & Support>",
                "",
                // "<" and ">" chars are accepted in on[Event] attribute values
                "<input type=\"image\" src=\"http://apologyindex.com/ThemeFiles/83401-72905/images/btn_search.gif\"value=\"Search\" name=\"Search\" alt=\"Search\" class=\"searchimage\" onclick=\"incom ='&sc=' + document.getElementById('sel').value ; var dt ='&dt=' + document.getElementById('dt').value; var searchKeyword = document.getElementById('q').value ; searchKeyword = searchKeyword.replace(/\\s/g,''); if (searchKeyword.length < 3){alert('Nothing to search. Search keyword should contain atleast 3 chars.'); return false; } var al='&al=' +  document.getElementById('advancedlink').style.display ;  document.location.href='http://apologyindex.com/search.aspx?q=' + document.getElementById('q').value + incom + dt + al;\" />",
                "",
                "<input type=\"image\" src=\"images/afbe.gif\" width=\"22\" height=\"22\"  hspace=\"4\" title=\"Add to Favorite\" alt=\"Add to Favorite\"onClick=\" if(window.sidebar){ window.sidebar.addPanel(document.title,location.href,''); }else if(window.external){ window.external.AddFavorite(location.href,document.title); }else if(window.opera&&window.print) { return true; }\">",
                "",
                "<area shape=\"rect\" coords=\"12,153,115,305\" href=\"http://statenislandtalk.com/v-web/gallery/Osmundsen-family\"Art's Norwegian Roots in Rogaland\">",
                "\n",
                "<a rel=\"nofollow\" href=\"http://arth26.skyrock.com/660188240-bonzai.html\" title=\"bonza>#",
                "#",
                "<a href=  >",
                "",
                "<ahref=http:..",
                "<ahref=http:..",
                "<ahref=http:..>",
                "\n",
                "<ahref=\"http://aseigo.bddf.ca/cms/1025\">A",
                "\nA",
                "<a href=\"javascript:calendar_window=window.open('/calendar.aspx?formname=frmCalendar.txtDate','calendar_window','width=154,height=188');calendar_window.focus()\">",
                "",
                "<a href=\"/applications/defenseaerospace/19+rackmounts\" title=\"19\" Rackmounts\">",
                "",
                "<a href=http://www.azimprimerie.fr/flash/backup/lewes-zip-code/savage-model-110-manual.html title=savage model 110 manual rel=dofollow>",
                "",
                "<a class=\"at\" name=\"Lamborghini  href=\"http://lamborghini.coolbegin.com\">Lamborghini /a>",
                "Lamborghini /a>",
                "<A href='newslink.php?news_link=http%3A%2F%2Fwww.worldnetdaily.com%2Findex.php%3Ffa%3DPAGE.view%26pageId%3D85729&news_title=Florida QB makes 'John 3:16' hottest Google search Tebow inscribed Bible reference on eye black for championship game' TARGET=_blank>",
                "",
                "<a href=/myspace !style='color:#993333'>",
                "",
                "<meta name=3DProgId content=3DExcel.Sheet>",
                "\n",
                "<link id=3D\"shLink\" href=3D\"PSABrKelly-BADMINTONCupResults08FINAL2008_09_19=_files/sheet004.htm\">",
                "\n",
                "<td bgcolor=3D\"#FFFFFF\" nowrap>",
                "\n",
                "<a href=\"http://basnect.info/usersearch/\"predicciones-mundiales-2009\".html\">\"predicciones mundiales 2009\"</a>",
                "\"predicciones mundiales 2009\"",
                "<a class=\"comment-link\" href=\"https://www.blogger.com/comment.g?blogID=19402125&postID=114070605958684588\"location.href=https://www.blogger.com/comment.g?blogID=19402125&postID=114070605958684588;>",
                "",
                "<a href = \"/videos/Bishop\"/\" title = \"click to see more Bishop\" videos\">Bishop\"</a>",
                "Bishop\"",
                "<a href=\"http://bhaa.ie/calendar/event.php?eid=20081203150127531\"\">BHAA Eircom 2 &amp; 5 miles CC combined start</a>",
                "BHAA Eircom 2 & 5 miles CC combined start",
                "<a href=\"http://people.tribe.net/wolfmana\" onClick='setClick(\"Application[tribe].Person[bb7df210-9dc0-478c-917f-436b896bcb79]\")'\" title=\"Mana\">",
                "",
                "<a  href=\"http://blog.edu-cyberpg.com/ct.ashx?id=6143c528-080c-4bb2-b765-5ec56c8256d3&url=http%3a%2f%2fwww.gsa.ac.uk%2fmackintoshsketchbook%2f\"\" eudora=\"autourl\">",
                "",
                // LUCENE-10520: "<" and ">" in attribute values is valid per the HTML5 spec
                "<input type=\"text\" value=\"<search here>\">",
                "",
                "<input type=\"text\" value=\"<search here\">",
                "",
                "<input type=\"text\" value=\"search here>\">",
                "",
                // "<" and ">" chars are accepted in on[Event] attribute values
                "<input type=\"text\" value=\"&lt;search here&gt;\" onFocus=\"this.value='<search here>'\">",
                "",
                "<![if ! IE]>\n<link href=\"http://i.deviantart.com/icons/favicon.png\" rel=\"shortcut icon\"/>\n<![endif]>",
                "\n\n\n",
                "<![if supportMisalignedColumns]>\n<tr height=0 style='display:none'>\n<td width=64 style='width:48pt'></td>\n</tr>\n<![endif]>",
                "\n\n\n\n\n\n\n\n"
            )
        for (i in testGold.indices step 2) {
            assertHTMLStripsTo(testGold[i], testGold[i + 1], null)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBufferOverflow() {
        val testBuilder = StringBuilder(HTMLStripCharFilter.getInitialBufferSize() + 50)
        testBuilder.append("ah<?> ??????")
        appendChars(testBuilder, HTMLStripCharFilter.getInitialBufferSize() + 500)
        val reader: Reader =
            HTMLStripCharFilter(
                BufferedReader(
                    StringReader(testBuilder.toString())
                )
            ) // force the use of BufferedReader
        assertHTMLStripsTo(reader, testBuilder.toString(), null)

        testBuilder.setLength(0)
        testBuilder.append("<!--") // comments
        appendChars(
            testBuilder,
            3 * HTMLStripCharFilter.getInitialBufferSize() + 500
        ) // comments have two lookaheads

        testBuilder.append("-->foo")
        var gold = "foo"
        assertHTMLStripsTo(testBuilder.toString(), gold, null)

        testBuilder.setLength(0)
        testBuilder.append("<?")
        appendChars(testBuilder, HTMLStripCharFilter.getInitialBufferSize() + 500)
        testBuilder.append("?>")
        gold = ""
        assertHTMLStripsTo(testBuilder.toString(), gold, null)

        testBuilder.setLength(0)
        testBuilder.append("<b ")
        appendChars(testBuilder, HTMLStripCharFilter.getInitialBufferSize() + 500)
        testBuilder.append("/>")
        gold = ""
        assertHTMLStripsTo(testBuilder.toString(), gold, null)
    }

    private fun appendChars(testBuilder: StringBuilder, numChars: Int) {
        val i1 = numChars / 2
        for (i in 0 until i1) {
            testBuilder
                .append('a')
                .append(' ') // tack on enough to go beyond the mark readahead limit, since <?> makes
            // HTMLStripCharFilter think it is a processing instruction
        }
    }

    @Test
    @Throws(Exception::class)
    fun testComment() {
        var test = "<!--- three dashes, still a valid comment ---> "
        var gold = " "
        assertHTMLStripsTo(test, gold, null)

        test = "<! -- blah > " // should not be recognized as a comment
        gold = " "
        assertHTMLStripsTo(test, gold, null)

        val testBuilder = StringBuilder("<!--")
        appendChars(testBuilder, TestUtil.nextInt(random(), 0, 1000))
        gold = ""
        assertHTMLStripsTo(testBuilder.toString(), gold, null)
    }

    @Throws(Exception::class)
    fun doTestOffsets(input: String) {
        val reader = HTMLStripCharFilter(BufferedReader(StringReader(input)))
        var ch = 0
        var off = 0 // offset in the reader
        var strOff = -1 // offset in the original string
        while (reader.read().also { ch = it } != -1) {
            val correctedOff = reader.correctOffset(off)

            if (ch == 'X'.code) {
                strOff = input.indexOf('X', strOff + 1)
                assertEquals(strOff, correctedOff)
            }

            off++
        }
    }

    @Test
    @Throws(Exception::class)
    fun testOffsets() {
        //    doTestOffsets("hello X how X are you");
        doTestOffsets("hello <p> X<p> how <p>X are you")
        doTestOffsets("X &amp; X &#40; X &lt; &gt; X")

        // test backtracking
        doTestOffsets("X < &zz >X &# < X > < &l > &g < X")
    }

    @Test
    @Throws(Exception::class)
    fun testLegalOffsets() {
        assertLegalOffsets("hello world")
        assertLegalOffsets("hello &#x world")
    }

    @Test
    @Throws(Exception::class)
    fun testRandom() {
        val numRounds = RANDOM_MULTIPLIER * 1000
        val analyzer = newTestAnalyzer()
        checkRandomData(random(), analyzer, numRounds)
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        val numRounds = RANDOM_MULTIPLIER * 100
        val analyzer = newTestAnalyzer()
        checkRandomData(random(), analyzer, numRounds, 8192)
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testCloseBR() {
        val analyzer = newTestAnalyzer()
        checkAnalysisConsistency(random(), analyzer, random().nextBoolean(), " Secretary)</br> [[M")
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testServerSideIncludes() {
        var test =
            "one<img src=\"image.png\"\n" +
                " alt =  \"Alt: <!--#echo var='\${IMAGE_CAPTION:<!--comment-->\\'Comment\\'}'  -->\"\n\n" +
                " title=\"Title: <!--#echo var=\"IMAGE_CAPTION\"-->\">two"
        var gold = "onetwo"
        assertHTMLStripsTo(test, gold, null)

        test = "one<script><!-- <!--#config comment=\"<!-- \\\"comment\\\"-->\"--> --></script>two"
        gold = "one\ntwo"
        assertHTMLStripsTo(test, gold, null)
    }

    @Test
    @Throws(Exception::class)
    fun testScriptQuotes() {
        var test =
            "one<script attr= bare><!-- action('<!-- comment -->', \"\\\"-->\\\"\"); --></script>two"
        var gold = "one\ntwo"
        assertHTMLStripsTo(test, gold, null)

        test = "hello<script><!-- f('<!--internal--></script>'); --></script>"
        gold = "hello\n"
        assertHTMLStripsTo(test, gold, null)
    }

    @Test
    @Throws(Exception::class)
    fun testEscapeScript() {
        val test = "one<script no-value-attr>callSomeMethod();</script>two"
        val gold = "one<script no-value-attr></script>two"
        val escapedTags = mutableSetOf("SCRIPT")
        assertHTMLStripsTo(test, gold, escapedTags)
    }

    @Test
    @Throws(Exception::class)
    fun testStyle() {
        val test =
            "one<style type=\"text/css\">\n" +
                "<!--\n" +
                "@import url('http://www.lasletrasdecanciones.com/css.css');\n" +
                "-->\n" +
                "</style>two"
        val gold = "one\ntwo"
        assertHTMLStripsTo(test, gold, null)
    }

    @Test
    @Throws(Exception::class)
    fun testEscapeStyle() {
        val test = "one<style type=\"text/css\"> body,font,a { font-family:arial; } </style>two"
        val gold = "one<style type=\"text/css\"></style>two"
        val escapedTags = mutableSetOf("STYLE")
        assertHTMLStripsTo(test, gold, escapedTags)
    }

    @Test
    @Throws(Exception::class)
    fun testBR() {
        val testGold =
            arrayOf(
                "one<BR />two<br>three",
                "one\ntwo\nthree",
                "one<BR some stuff here too>two</BR>",
                "one\ntwo\n"
            )
        for (i in testGold.indices step 2) {
            assertHTMLStripsTo(testGold[i], testGold[i + 1], null)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testEscapeBR() {
        val test = "one<BR class='whatever'>two</\nBR\n>"
        val gold = "one<BR class='whatever'>two</\nBR\n>"
        val escapedTags = mutableSetOf("BR")
        assertHTMLStripsTo(test, gold, escapedTags)
    }

    @Test
    @Throws(Exception::class)
    fun testInlineTagsNoSpace() {
        val test = "one<sPAn class=\"invisible\">two<sup>2<sup>e</sup></sup>.</SpaN>three"
        val gold = "onetwo2e.three"
        assertHTMLStripsTo(test, gold, null)
    }

    @Test
    @Throws(Exception::class)
    fun testCDATA() {
        val maxNumElems = 100
        val randomHtmlishString1 =
            TestUtil.randomHtmlishString(random(), maxNumElems)
                .replace('>', ' ')
                .replaceFirst("^--".toRegex(), "__")
        val closedAngleBangNonCDATA = "<!$randomHtmlishString1-[CDATA[&]]>"

        // Don't create a comment (disallow "<!--") and don't include a closing ">"
        val randomHtmlishString2 =
            TestUtil.randomHtmlishString(random(), maxNumElems)
                .replace('>', ' ')
                .replaceFirst("^--".toRegex(), "__")
        val unclosedAngleBangNonCDATA = "<!$randomHtmlishString2-[CDATA["

        val testGold =
            arrayOf(
                "one<![CDATA[<one><two>three<four></four></two></one>]]>two",
                "one<one><two>three<four></four></two></one>two",
                "one<![CDATA[two<![CDATA[three]]]]><![CDATA[>four]]>five",
                "onetwo<![CDATA[three]]>fourfive",
                "<! [CDATA[&]]>",
                "",
                "<! [CDATA[&] ] >",
                "",
                "<! [CDATA[&]]",
                "<! [CDATA[&]]", // unclosed angle bang - all input is output
                "<!\u2009[CDATA[&]]>",
                "",
                "<!\u2009[CDATA[&]\u2009]\u2009>",
                "",
                "<!\u2009[CDATA[&]\u2009]\u2009",
                "<!\u2009[CDATA[&]\u2009]\u2009", // unclosed angle bang - all input is output
                closedAngleBangNonCDATA,
                "",
                "<![CDATA[",
                "",
                "<![CDATA[<br>",
                "<br>",
                "<![CDATA[<br>]]",
                "<br>]]",
                "<![CDATA[<br>]]>",
                "<br>",
                "<![CDATA[<br>] ] >",
                "<br>] ] >",
                "<![CDATA[<br>]\u2009]\u2009>",
                "<br>]\u2009]\u2009>",
                "<!\u2009[CDATA[",
                "<!\u2009[CDATA[",
                unclosedAngleBangNonCDATA,
                unclosedAngleBangNonCDATA
            )
        for (i in testGold.indices step 2) {
            assertHTMLStripsTo(testGold[i], testGold[i + 1], null)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testUnclosedAngleBang() {
        assertHTMLStripsTo("<![endif]", "<![endif]", null)
    }

    @Test
    @Throws(Exception::class)
    fun testUppercaseCharacterEntityVariants() {
        val test = " &QUOT;-&COPY;&GT;>&LT;<&REG;&AMP;"
        val gold = " \"-\u00A9>><<\u00AE&"
        assertHTMLStripsTo(test, gold, null)
    }

    @Test
    @Throws(Exception::class)
    fun testMSWordMalformedProcessingInstruction() {
        val test =
            "one<?xml:namespace prefix = o ns = \"urn:schemas-microsoft-com:office:office\" />two"
        val gold = "onetwo"
        assertHTMLStripsTo(test, gold, null)
    }

    @Test
    @Throws(Exception::class)
    fun testSupplementaryCharsInTags() {
        val test = "one<𩬅艱鍟䇹愯瀛>two<瀛愯𩬅>three 瀛愯𩬅</瀛愯𩬅>four</𩬅艱鍟䇹愯瀛>five<𠀀𠀀>six<𠀀𠀀/>seven"
        val gold = "one\ntwo\nthree 瀛愯𩬅\nfour\nfive\nsix\nseven"
        assertHTMLStripsTo(test, gold, null)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomBrokenHTML() {
        val maxNumElements = 10000
        val text = TestUtil.randomHtmlishString(random(), maxNumElements)
        val analyzer = newTestAnalyzer()
        checkAnalysisConsistency(random(), analyzer, random().nextBoolean(), text)
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomText() {
        val text = StringBuilder()
        val minNumWords = 10
        val maxNumWords = 10000
        val minWordLength = 3
        val maxWordLength = 20
        val numWords = TestUtil.nextInt(random(), minNumWords, maxNumWords)
        when (TestUtil.nextInt(random(), 0, 4)) {
            0 -> {
                for (wordNum in 0 until numWords) {
                    text.append(TestUtil.randomUnicodeString(random(), maxWordLength))
                    text.append(' ')
                }
            }

            1 -> {
                for (wordNum in 0 until numWords) {
                    text.append(
                        TestUtil.randomRealisticUnicodeString(
                            random(),
                            minWordLength,
                            maxWordLength
                        )
                    )
                    text.append(' ')
                }
            }

            else -> { // ASCII 50% of the time
                for (wordNum in 0 until numWords) {
                    text.append(TestUtil.randomSimpleString(random()))
                    text.append(' ')
                }
            }
        }
        val reader: Reader = HTMLStripCharFilter(StringReader(text.toString()))
        while (reader.read() != -1) {
            // consume
        }
    }

    @Test
    @Throws(Exception::class)
    fun testUTF16Surrogates() {
        val analyzer = newTestAnalyzer()
        // Paired surrogates
        assertAnalyzesTo(analyzer, " one two &#xD86C;&#XdC01;three", arrayOf("one", "two", "\uD86C\uDC01three"))
        assertAnalyzesTo(analyzer, " &#55404;&#XdC01;", arrayOf("\uD86C\uDC01"))
        assertAnalyzesTo(analyzer, " &#xD86C;&#56321;", arrayOf("\uD86C\uDC01"))
        assertAnalyzesTo(analyzer, " &#55404;&#56321;", arrayOf("\uD86C\uDC01"))

        // Improperly paired surrogates
        assertAnalyzesTo(analyzer, " &#55404;&#57999;", arrayOf("\uFFFD\uE28F"))
        assertAnalyzesTo(analyzer, " &#xD86C;&#57999;", arrayOf("\uFFFD\uE28F"))
        assertAnalyzesTo(analyzer, " &#55002;&#XdC01;", arrayOf("\uD6DA\uFFFD"))
        assertAnalyzesTo(analyzer, " &#55002;&#56321;", arrayOf("\uD6DA\uFFFD"))

        // Unpaired high surrogates
        assertAnalyzesTo(analyzer, " &#Xd921;", arrayOf("\uFFFD"))
        assertAnalyzesTo(analyzer, " &#Xd921", arrayOf("\uFFFD"))
        assertAnalyzesTo(analyzer, " &#Xd921<br>", arrayOf("&#Xd921"))
        assertAnalyzesTo(analyzer, " &#55528;", arrayOf("\uFFFD"))
        assertAnalyzesTo(analyzer, " &#55528", arrayOf("\uFFFD"))
        assertAnalyzesTo(analyzer, " &#55528<br>", arrayOf("&#55528"))

        // Unpaired low surrogates
        assertAnalyzesTo(analyzer, " &#xdfdb;", arrayOf("\uFFFD"))
        assertAnalyzesTo(analyzer, " &#xdfdb", arrayOf("\uFFFD"))
        assertAnalyzesTo(analyzer, " &#xdfdb<br>", arrayOf("&#xdfdb"))
        assertAnalyzesTo(analyzer, " &#57209;", arrayOf("\uFFFD"))
        assertAnalyzesTo(analyzer, " &#57209", arrayOf("\uFFFD"))
        assertAnalyzesTo(analyzer, " &#57209<br>", arrayOf("&#57209"))
        analyzer.close()
    }

    /**
     * Test that attributes with {@code '>'} or {@code '<'} characters are parsed correctly.
     *
     * @see <a href="https://github.com/apache/lucene/issues/11556">GITHUB#11556</a>
     * @throws IOException on IO error
     */
    @Test
    @Throws(IOException::class)
    fun testForIssue10520() {
        val test =
            "<!DOCTYPE html><html lang=\"en\"><head><title>Test</title></head><body><p class=\"foo>bar\" id=\"baz\">Some text.</p></body></html>"
        val reader: Reader = StringReader(test)
        val filter = HTMLStripCharFilter(reader)
        val result = StringWriter()
        filter.transferTo(result)
        assertEquals("Test\n\n\n\nSome text.", result.toString().trim())
    }

    companion object {
        private fun newTestAnalyzer(): Analyzer {
            return object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        MockTokenizer(MockTokenizer.WHITESPACE, false, IndexWriter.MAX_TERM_LENGTH / 2)
                    return TokenStreamComponents(tokenizer, tokenizer)
                }

                override fun initReader(fieldName: String, reader: Reader): Reader {
                    return HTMLStripCharFilter(reader)
                }
            }
        }

        @Throws(Exception::class)
        private fun assertLegalOffsets(input: String) {
            val length = input.length
            val reader = HTMLStripCharFilter(BufferedReader(StringReader(input)))
            var off = 0
            while (reader.read() != -1) {
                val correction = reader.correctOffset(off)
                assertTrue(
                    correction <= length,
                    "invalid offset correction: $off->$correction for doc of length: $length"
                )
                off++
            }
        }

        @Throws(Exception::class)
        fun assertHTMLStripsTo(input: String, gold: String, escapedTags: MutableSet<String>?) {
            assertHTMLStripsTo(StringReader(input), gold, escapedTags)
        }

        @Throws(Exception::class)
        fun assertHTMLStripsTo(input: Reader, gold: String, escapedTags: MutableSet<String>?) {
            val reader =
                if (escapedTags == null) {
                    HTMLStripCharFilter(input)
                } else {
                    HTMLStripCharFilter(input, escapedTags)
                }
            var ch = 0
            val builder = StringBuilder()
            try {
                while (reader.read().also { ch = it } != -1) {
                    builder.append(ch.toChar())
                }
            } catch (e: Exception) {
                if (gold.contentEquals(builder)) {
                    throw e
                }
                throw Exception("('${builder}' is not equal to '$gold').  ${e.message}", e)
            }
            assertEquals(gold, builder.toString(), "'$builder' is not equal to '$gold'")
        }
    }
}
