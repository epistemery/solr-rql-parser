package org.epistemery.solr.plugins;

import org.apache.solr.SolrTestCaseJ4;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestRqlQParserPlugin extends SolrTestCaseJ4 {
    @BeforeClass
    public static void beforeClass() throws Exception {
        initCore("solr/conf/solrconfig.xml", "solr/conf/schema.xml", "src/test/resources/tmp/solr", "core-1");
    }

    @Before
    public void before() throws Exception {
        clearIndex();
        index();
    }

    @Test
    public void testEq() throws Exception {
        assertJQ(req("defType", "javascript", "q", "eq(id,42)"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "eq(text,lorem)"), "/response/numFound==2");
        assertJQ(req("defType", "javascript", "q", "eq(number,1886)"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "eq(bool,false)"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "eq(bool,true)"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "eq(number,123)"), "/response/numFound==0");
    }

    @Test
    public void testNe() throws Exception {
        assertJQ(req("defType", "javascript", "q", "and(eq(text,lorem),ne(id,42))"), "/response/numFound==1");
    }

    @Test
    public void testContains() throws Exception {
        assertJQ(req("defType", "javascript", "q", "contains(keywords,def)"), "/response/numFound==2");
        assertJQ(req("defType", "javascript", "q", "contains(keywords,abc)"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "contains(keywords,xyz)"), "/response/numFound==0");
    }

    @Test
    public void testExcludes() throws Exception {
        assertJQ(req("defType", "javascript", "q", "excludes(keywords,xyz)"), "/response/numFound==2");
        assertJQ(req("defType", "javascript", "q", "excludes(keywords,abc)"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "excludes(keywords,def)"), "/response/numFound==0");
    }

    @Test
    public void testAnd() throws Exception {
        assertJQ(req("defType", "javascript", "q", "and(eq(same,same),contains(keywords,def))"), "/response/numFound==2");
        assertJQ(req("defType", "javascript", "q", "and(eq(same,same),eq(id,42))"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "and(eq(id,43),eq(number,1886))"), "/response/numFound==0");
    }

    @Test
    public void testOr() throws Exception {
        assertJQ(req("defType", "javascript", "q", "or(eq(id,42),eq(id,43))"), "/response/numFound==2");
        assertJQ(req("defType", "javascript", "q", "or(eq(id,43),eq(id,44))"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "or(eq(id,44),eq(id,45))"), "/response/numFound==0");
    }

    @Test
    public void testIn() throws Exception {
        assertJQ(req("defType", "javascript", "q", "in(id,42,43)"), "/response/numFound==2");
        assertJQ(req("defType", "javascript", "q", "in(id,43,44,45)"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "in(id,44,45)"), "/response/numFound==0");
    }

    @Test
    public void testOut() throws Exception {
        assertJQ(req("defType", "javascript", "q", "out(id,44,45)"), "/response/numFound==2");
        assertJQ(req("defType", "javascript", "q", "out(id,43,44,45)"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "out(id,42,43)"), "/response/numFound==0");
    }

    @Test
    public void testGt() throws Exception {
        assertJQ(req("defType", "javascript", "q", "gt(number,1886)"), "/response/numFound==1");
    }

    @Test
    public void testLt() throws Exception {
        assertJQ(req("defType", "javascript", "q", "lt(number,1887)"), "/response/numFound==1");
    }

    @Test
    public void testGe() throws Exception {
        assertJQ(req("defType", "javascript", "q", "ge(number,1886)"), "/response/numFound==2");
    }

    @Test
    public void testLe() throws Exception {
        assertJQ(req("defType", "javascript", "q", "le(number,1887)"), "/response/numFound==2");
    }

    // non-standard RQL operators
    @Test
    public void testAll() throws Exception {
        assertJQ(req("defType", "javascript", "q", "all()"), "/response/numFound==2");
    }

    @Test
    public void testIs() throws Exception {
        assertJQ(req("defType", "javascript", "q", "is(42)"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "is(44)"), "/response/numFound==0");
    }

    @Test
    public void testFilter() throws Exception {
        assertJQ(req("defType", "javascript", "q", "filter(eq(same,same),contains(keywords,def))"), "/response/numFound==2");
        assertJQ(req("defType", "javascript", "q", "filter(eq(same,same),eq(id,42))"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "filter(eq(id,43),eq(number,1886))"), "/response/numFound==0");
    }

    @Test
    public void testBoost() throws Exception {
        // TODO. this does not really test the boost, but checks if it behaves the same as any other query
        assertJQ(req("defType", "javascript", "q", "boost(2,eq(bool,true))"), "/response/numFound==1");
    }

    @Test
    public void testSolr() throws Exception {
        assertJQ(req(
                "defType", "javascript",
                "df", "text",
                "q", "solr(simple,lorem%20ipsum)"), "/response/numFound==2");
        assertJQ(req("defType", "javascript", "q", "solr(number%3A1887)"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "solr(number%3A1888)"), "/response/numFound==0");
    }

    // non-standard RQL converters
    @Test
    public void testConvertLiteral() throws Exception {
        assertJQ(req("defType", "javascript", "q", "eq(text,literal:lorem%20ipsum)"), "/response/numFound==1");
        assertJQ(req("defType", "javascript", "q", "eq(text,literal:ipsum%20lorem)"), "/response/numFound==0");
    }

    @Test
    public void testConvertMask() throws Exception {
        assertJQ(req("defType", "javascript", "q", "eq(text,mask:lore*)"), "/response/numFound==2");
    }

    @Test
    public void testBooleanCombinations() throws Exception {
        clearIndex();

        assertU(adoc( "id", "1", "text", "abc", "bool", "true"));
        assertU(adoc( "id", "2", "text", "def", "bool", "true"));
        assertU(adoc( "id", "3", "text", "ghi", "bool", "true"));
        assertU(adoc( "id", "4", "text", "abc def", "bool", "true"));
        assertU(adoc( "id", "5", "text", "abc ghi", "bool", "false"));
        assertU(adoc( "id", "6", "text", "def ghi", "bool", "true"));
        assertU(adoc( "id", "7", "text", "abc def ghi", "bool", "false"));
        assertU(commit());

        assertJQ(req("defType", "javascript", "q", "and(eq(text,abc),eq(text,ghi))"), "/response/numFound==2");
        assertJQ(req("defType", "javascript", "q", "or(and(eq(text,abc),eq(text,ghi)),and(eq(text,def),eq(text,ghi)))"), "/response/numFound==3");
        assertJQ(req("defType", "javascript", "q", "and(or(eq(text,abc),eq(text,ghi)),or(eq(text,def),eq(text,ghi)))"), "/response/numFound==5");
        assertJQ(req("defType", "javascript", "q", "and(or(eq(text,abc),eq(text,def)),or(eq(text,def),eq(text,ghi)))"), "/response/numFound==5");
        assertJQ(req("defType", "javascript", "q", "or(and(eq(text,abc),eq(text,def),eq(bool,true)),and(eq(text,def),eq(text,ghi),eq(bool,true)))"), "/response/numFound==2");
        assertJQ(req("defType", "javascript", "q", "ne(text,ghi)"), "/response/numFound==3");
        assertJQ(req("defType", "javascript", "q", "or(eq(text,ghi),ne(text,ghi))"), "/response/numFound==7");
        assertJQ(req("defType", "javascript", "q", "and(eq(text,ghi),ne(text,ghi))"), "/response/numFound==0");
        assertJQ(req("defType", "javascript", "q", "or(and(eq(text,ghi),ne(text,ghi)),eq(bool,false))"), "/response/numFound==2");
    }

    @Test
    public void testSort() throws Exception {
        clearIndex();

        assertU(adoc( "id", "1", "same", "abc", "bool", "true"));
        assertU(adoc( "id", "2", "same", "def", "bool", "true"));
        assertU(adoc( "id", "3", "same", "ghi", "bool", "true"));
        assertU(adoc( "id", "4", "same", "abc def", "bool", "true"));
        assertU(adoc( "id", "5", "same", "abc ghi", "bool", "false"));
        assertU(adoc( "id", "6", "same", "abc ghi", "bool", "true"));
        assertU(adoc( "id", "7", "same", "def ghi", "bool", "true"));
        assertU(adoc( "id", "8", "same", "abc def ghi", "bool", "false"));
        assertU(commit());

        assertJQ(
                req("defType", "javascript", "q", "and(all(),sort(+same,-bool))"),
                "/response/docs/[0]/id=='1'",
                "/response/docs/[1]/id=='4'",
                "/response/docs/[2]/id=='8'",
                "/response/docs/[3]/id=='6'",
                "/response/docs/[4]/id=='5'",
                "/response/docs/[5]/id=='2'",
                "/response/docs/[6]/id=='7'",
                "/response/docs/[7]/id=='3'");

        assertJQ(
                req("defType", "javascript", "q", "and(all(),sort(+same),sort(bool))"),
                "/response/docs/[0]/id=='1'",
                "/response/docs/[1]/id=='4'",
                "/response/docs/[2]/id=='8'",
                "/response/docs/[3]/id=='6'",
                "/response/docs/[4]/id=='5'",
                "/response/docs/[5]/id=='2'",
                "/response/docs/[6]/id=='7'",
                "/response/docs/[7]/id=='3'");

        assertJQ(
                req("defType", "javascript", "sort", "bool asc, same asc", "q", "and(all(),sort(+same,-bool))"),
                "/response/docs/[0]/id=='8'",
                "/response/docs/[1]/id=='5'",
                "/response/docs/[2]/id=='1'",
                "/response/docs/[3]/id=='4'",
                "/response/docs/[4]/id=='6'",
                "/response/docs/[5]/id=='2'",
                "/response/docs/[6]/id=='7'",
                "/response/docs/[7]/id=='3'");
    }

    @Test
    public void testLimit() throws Exception {
        assertQ(req("defType", "javascript", "q", "and(all(),limit(1,0))"), "count(//doc)=1");
        assertQ(req("defType", "javascript", "q", "and(all(),limit(1,1))"), "count(//doc)=1");
        assertQ(req("defType", "javascript", "q", "and(all(),limit(2,0))"), "count(//doc)=2");
    }

    public static void index() throws Exception {
        assertU(adoc(
                "id", "42",
                "uri", "urn:42",
                "same", "same",
                "text", "lorem ipsum",
                "number", "1886",
                "bool", "true",
                "keywords", "abc",
                "keywords", "def"));
        assertU(adoc(
                "id", "43",
                "uri", "urn:43",
                "same", "same",
                "text", "lorem",
                "number", "1887",
                "bool", "false",
                "keywords", "def"));
        assertU(commit());
    }
}

