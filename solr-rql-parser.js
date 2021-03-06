"use strict";

var rql_parser = require("rql/parser");

var MatchAllDocsQuery = Java.type("org.apache.lucene.search.MatchAllDocsQuery");
var BooleanQuery = Java.type("org.apache.lucene.search.BooleanQuery");
var BooleanClause = Java.type("org.apache.lucene.search.BooleanClause");
var BoostQuery = Java.type("org.apache.lucene.search.BoostQuery");
var TermQuery = Java.type("org.apache.lucene.search.TermQuery");
var PhraseQuery = Java.type("org.apache.lucene.search.PhraseQuery");
var SolrRangeQuery = Java.type("org.apache.solr.query.SolrRangeQuery");
var Term = Java.type("org.apache.lucene.index.Term");
var QueryParsing = Java.type("org.apache.solr.search.QueryParsing");
var BytesRefBuilder = Java.type("org.apache.lucene.util.BytesRefBuilder");
var QueryBuilder = Java.type("org.apache.lucene.util.QueryBuilder");
var RqlQueryParserBase = Java.extend(Java.type("org.apache.solr.parser.SolrQueryParserBase"), {
    ReInit: function(stream) {

    },
    TopLevelQuery: function(field) {
        return null;
    }
});

var ModifiableSolrParams = Java.type("org.apache.solr.common.params.ModifiableSolrParams");

// debug function because Query instance is not JSON-serializable
rql_parser.Query.prototype.toObject = function() {
    var obj = {};
    obj.name = this.name;
    obj.args = [];
    this.args.forEach(function(e) {
        if (e instanceof rql_parser.Query) {
            obj.args.push(e.toObject());
        } else if (typeof e !== "string" && e.length !== undefined) {
            obj.args.push(Array.prototype.map.call(e, function(f) { return f.toString(); }));
        } else {
            obj.args.push(e.toString());
        }
    });

    return obj;
};

function LiteralString(str) {
    this.str = str;
    this.toString = function() {
        return "'" + decodeURIComponent(str) + "'";
    };
}

function WildcardString(str) {
    this.toString = function() {
        return decodeURIComponent(str);
    };
}

function value_to_bytesref(key, value) {
    value = String(value);
    var field = this.schema.getFieldTypeNoEx(key);
    var term = new BytesRefBuilder();

    if (field != null) {
        field.readableToIndexed(value, term);
    } else {
        term.copyChars(value);
    }

    return term.get();
}

function assemble_term_query(key, value, scope) {
    var field = this.getfield(key, scope);

    // in case we need this some day (minShouldMatchQuery):
    //return builder.createMinShouldMatchQuery("body", "another test", 0.5f);

    if (value instanceof LiteralString) {
        return this.querybuilder.createPhraseQuery(field, this.walk(value));
    }
    if (value instanceof WildcardString) {
        return this.parserbase.getWildcardQuery(field, this.walk(value));
    }

    return this.querybuilder.createBooleanQuery(field, this.walk(value));
}

function forEachArg(args, func) {
    Array.prototype.forEach.call(args, function (arg) {
        func(arg);
    });
}

function walk(query, scope) {
    if (query instanceof rql_parser.Query) {
        if(!this.ops[query.name]) {
            throw new Error("'" + query.name + "' is not a valid query function");
        }
        return this.ops[query.name](query.args, scope);
    }

    return this.getvalue(query, scope);
}

function parse() {
    var parsed_rql = rql_parser.parse(this.qparser.getQstr());
    //console.log("parsed RQL: " + JSON.stringify(parsed_rql.toObject(), null, 2));

    var query = this.walk(parsed_rql);
    //console.info("generated query: " + query.toString());

    var newParams = new ModifiableSolrParams(this.qparser.getParams());

    if(this.sort !== undefined) {
        newParams.set("sort", [this.sort.join(",")]);
    }
    if(this.rows !== undefined && this.start !== undefined) {
        newParams.set("rows", parseInt(this.rows));
        newParams.set("start", parseInt(this.start));
    }

    this.qparser.setParams(newParams);

    return query;
}

function RqlSolrParser(qparser) {
    this.qparser = qparser;
    this.ops.parser = this;

    for(var p in this.converters) {
        if(this.converters.hasOwnProperty(p)) {
            rql_parser.converters[p] = this.converters[p];
        }
    }

    this.value_to_bytesref = value_to_bytesref;
    this.assemble_term_query = assemble_term_query;
    this.forEachArg = forEachArg;
    this.walk = walk;
    this.parse = parse;
    this.rql_parser = rql_parser;

    this.schema = this.qparser.getReq().getSchema();
    this.querybuilder = new QueryBuilder(this.schema.getQueryAnalyzer());
    this.parserbase = new RqlQueryParserBase();
    this.parserbase.init(null, null, this.qparser);
}

RqlSolrParser.prototype.getfield = function(key, scope) {
    return key;
};

RqlSolrParser.prototype.getvalue = function(val, scope) {
    return val;
};

RqlSolrParser.prototype.ops = {
    "eq": function(args, scope) {
        var key = args[0];
        var val = args[1];
        var query = this.parser.assemble_term_query(key, val, scope);

        return query;
    },
    "ne": function(args, scope) {
        var key = args[0];
        var val = args[1];

        var builder = new BooleanQuery.Builder();
        var ne_query = this.parser.assemble_term_query(key, val, scope);

        builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        builder.add(ne_query, BooleanClause.Occur.MUST_NOT);

        var query = builder.build();

        return query;
    },
    "and": function(args, scope) {
        var parser = this.parser;
        var builder = new BooleanQuery.Builder();
        parser.forEachArg(args, function (arg) {
            var subq = parser.walk(arg, scope);
            if(subq) {
                builder.add(subq, BooleanClause.Occur.MUST);
            }
        });

        var query = builder.build();

        return query;
    },
    "or": function(args, scope) {
        var parser = this.parser;
        var builder = new BooleanQuery.Builder();
        parser.forEachArg(args, function (arg) {
            var subq = parser.walk(arg, scope);
            if(subq) {
                builder.add(subq, BooleanClause.Occur.SHOULD);
            }
        });

        var query = builder.build();

        return query;
    },
    "in": function(args, scope) {
        var self = this;
        var parser = this.parser;
        var key = args.shift();
        var builder = new BooleanQuery.Builder();

        parser.forEachArg(args, function (arg) {
            builder.add(self["eq"]([key, arg], scope), BooleanClause.Occur.SHOULD);
        });

        var query = builder.build();

        return query;
    },
    "out": function(args, scope) {
        var parser = this.parser;
        var key = args.shift();
        var builder = new BooleanQuery.Builder();

        builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        parser.forEachArg(args, function (arg) {
            builder.add(parser.assemble_term_query(key, arg, scope), BooleanClause.Occur.MUST_NOT);
        });

        var query = builder.build();

        return query;
    },
    "gt": function(args, scope) {
        var key = args[0];
        var value = args[1];

        var query = new SolrRangeQuery(
            this.parser.getfield(key, scope),
            this.parser.value_to_bytesref(key, value),
            null,
            false,
            true);

        return query;
    },
    "ge": function(args, scope) {
        var key = args[0];
        var value = args[1];

        var query = new SolrRangeQuery(
            this.parser.getfield(key, scope),
            this.parser.value_to_bytesref(key, value),
            null,
            true,
            true);

        return query;
    },
    "lt": function(args, scope) {
        var key = args[0];
        var value = args[1];

        var query = new SolrRangeQuery(
            this.parser.getfield(key, scope),
            null,
            this.parser.value_to_bytesref(key, value),
            true,
            false);

        return query;
    },
    "le": function(args, scope) {
        var key = args[0];
        var value = args[1];

        var query = new SolrRangeQuery(
            this.parser.getfield(key, scope),
            null,
            this.parser.value_to_bytesref(key, value),
            true,
            true);

        return query;
    },
    // resultset modifiers
    "sort": function(args, scope) {
        var parser = this.parser;
        this.parser.forEachArg(args, function(arg){
            if(parser.sort === undefined) {
                parser.sort = [];
            }
            var arg = parser.getfield(arg, scope);
            var dir = arg.slice(0,1);
            if(dir === "+") {
                parser.sort.push(arg.slice(1) + " asc");
            } else if(dir === "-") {
                parser.sort.push(arg.slice(1) + " desc");
            } else {
                parser.sort.push(arg + " asc");
            }
        });
    },
    "limit": function(args, scope) {
        this.parser.rows = args[0];
        this.parser.start = args[1];
    },

    // non-standard RQL operators
    "regexp": function(args, scope) {
        var key = args[0];
        var regexp = args[1];

        var query = this.parser.parserbase.getRegexpQuery(this.parser.getfield(key, scope), regexp);

        return query;
    },
    "prefix": function(args, scope) {
        var key = args[0];
        var prefix = args[1];

        var query = this.parser.parserbase.getPrefixQuery(this.parser.getfield(key, scope), prefix);

        return query;
    },
    "all": function(args, scope) {
        var query = new MatchAllDocsQuery();

        return query;
    },
    "is": function(args, scope) {
        args.unshift("id");

        return this["in"](args);
    },
    "isin": function(args, scope) {
        args.unshift("id");

        return this["in"](args, scope);
    },
    "filter": function(args, scope) {
        var parser = this.parser;
        var builder = new BooleanQuery.Builder();
        parser.forEachArg(args, function (arg) {
            var subq = parser.walk(arg, scope);
            if(subq) {
                builder.add(subq, BooleanClause.Occur.FILTER);
            }
        });

        var query = builder.build();

        return query;
    },
    "boost": function(args, scope) {
        var boost = args.shift();
        var query = new BoostQuery(this.parser.walk(args[0], scope), parseFloat(boost));

        return query;
    },
    "solr": function(args, scope) {
        var
            defType = null,
            qstr = "";

        if (args.length == 2) {
            defType = args[0];
            qstr = args[1];
        } else {
            qstr = args[0];
        }

        var query = this.parser.qparser.subQuery(qstr, defType).parse();

        return query;
    }
};

// solr query for multivalued fields is the same as for single valued fields
RqlSolrParser.prototype.ops.contains = RqlSolrParser.prototype.ops["eq"];
RqlSolrParser.prototype.ops.excludes = RqlSolrParser.prototype.ops["ne"];

RqlSolrParser.prototype.converters = {
    "literal": function (str) {
        return new LiteralString(str);
    },
    "mask": function (str) {
        return new WildcardString(str);
    }
};


module.exports = RqlSolrParser;
