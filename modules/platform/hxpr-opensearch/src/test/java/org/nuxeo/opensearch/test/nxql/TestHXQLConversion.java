/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nuxeo.opensearch.test.nxql;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.json.stream.JsonGenerator;

import com.hyland.hxpr.async.tasks.TaskService;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.query.sql.HXQL;
import org.nuxeo.ecm.core.query.sql.model.FromClause;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.query.sql.model.WhereClause;
import org.nuxeo.opensearch.api.OpenSearchClient;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.opensearch.query.HXQLQueryConverter;
import org.nuxeo.opensearch.query.OpenSearchQueryBuilder;
import org.nuxeo.opensearch.test.RepositoryOpenSearchFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.opensearch._types.SearchType;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Test that NXQL can be used to generate ES queries
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 */
@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
public class TestHXQLConversion {

    private static final String IDX_NAME = "nxutest";

    @Inject
    protected CoreSession session;

    @Inject
    protected OpenSearchService oss;

    private HXQLQueryConverter nxqlQueryConverter;

    @BeforeEach
    public void setUp() {
        nxqlQueryConverter = new HXQLQueryConverter(session.getSchemaManager());
    }

    protected void buildDocs() throws Exception {
        for (int i = 0; i < 10; i++) {
            String name = "doc" + i;
            DocumentModel doc = session.createDocumentModel("/", name, SYS_FILE);
            doc.setPropertyValue(SYS_TITLE, "File" + i);
            doc.setPropertyValue("mydc_nature", "Nature" + i);
            doc.setPropertyValue("mydc_rights", "Rights" + i % 2);
            doc.create();
        }
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        // wait for async tasks
        TaskService asyncTasksService = Framework.getService(TaskService.class);
        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(20L)));

        oss.refresh();

        TransactionHelper.startTransaction();

    }

    protected SearchResponse<DocumentDTO> searchAll() {
        SearchRequest request = new SearchRequest.Builder().index(IDX_NAME) //
                                                           .searchType(SearchType.DfsQueryThenFetch)
                                                           .from(0)
                                                           .size(60)
                                                           .build();
        return oss.getClient().search(request);
    }

    protected SearchResponse<DocumentDTO> search(Query query) {
        SearchRequest request = new SearchRequest.Builder().index(IDX_NAME) //
                                                           .searchType(SearchType.DfsQueryThenFetch)
                                                           .from(0)
                                                           .size(60)
                                                           .query(query)
                                                           .build();
        return oss.getClient().search(request);
    }

    @Test
    public void testQuery() throws Exception {

        buildDocs();

        Query query = QueryBuilders.queryString()
                                   .query(" mydc\\_nature:\"Nature1\" AND sys\\_title:\"File1\"")
                                   .build()
                                   .toQuery();
        SearchResponse<DocumentDTO> searchResponse = search(query);
        assertEquals(1, searchResponse.hits().total().value());

        query = QueryBuilders.queryString()
                             .query(" mydc\\\\_nature:\"Nature2\" AND sys\\_title:\"File1\"")
                             .build()
                             .toQuery();
        searchResponse = search(query);
        assertEquals(0, searchResponse.hits().total().value());

        query = QueryBuilders.queryString().query(" NOT " + "mydc\\\\_nature:\"Nature2\"").build().toQuery();
        searchResponse = search(query);
        assertEquals(10, searchResponse.hits().total().value());

        checkNXQL("select * from SysContent where mydc_nature='Nature2' and sys_title='File2'", 1);
        checkNXQL("select * from SysContent where mydc_nature='Nature2' and sys_title='File1'", 0);
        checkNXQL("select * from SysContent where mydc_nature='Nature2' or sys_title='File1'", 2);
    }

    @Test
    public void testQueryLimits() throws Exception {
        buildDocs();

        // limit does not change the total size, only the returned number of docs
        DocumentModelList docs = oss.query(new OpenSearchQueryBuilder(session).hxql("select * FROM SysContent").limit(1));
        assertEquals(10, docs.totalSize());
        assertEquals(1, docs.size());
        // default is 10
        docs = oss.query(new OpenSearchQueryBuilder(session).hxql("select * FROM SysContent"));
        assertEquals(10, docs.totalSize());
        assertEquals(10, docs.size());
        // only interested about totalSize
        docs = oss.query(new OpenSearchQueryBuilder(session).hxql("select * FROM SysContent").limit(0));
        assertEquals(10, docs.totalSize());
        assertEquals(0, docs.size());
    }

    @Test
    public void testQueryWithSpecialCharacters() {
        // special character should not raise syntax error
        String specialChars = "^..*+ - && || ! ( ) { } [ ] )^ \" (~ * ? : \\\\ / \\t$";
        checkNXQL("select * FROM SysContent where sys_title = '" + specialChars + "'", 0);
        checkNXQL("select * FROM SysContent where sys_fulltext.sys_title = '" + specialChars + "'", 0);
        checkNXQL("select * FROM SysContent where sys_title LIKE '" + specialChars + "'", 0);
        checkNXQL("select * FROM SysContent where sys_title IN ('" + specialChars + "')", 0);
        checkNXQL("select * FROM SysContent where sys_title STARTSWITH '" + specialChars + "'", 0);
    }

    protected void checkNXQL(String nxql, int expectedNumberOfHis) {
        // System.out.println(nxqlQueryConverter.toESQueryString(nxql));
        DocumentModelList docs = oss.query(new OpenSearchQueryBuilder(session).hxql(nxql).limit(0));
        assertEquals(expectedNumberOfHis, docs.totalSize());
    }

    @Test
    public void testConverterSelect() {
        String es = toESQueryBuilder("select * FROM SysContent");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "match_all" : {
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysFile, SysContent");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "match_all" : {
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "MyFile",
                            "SysFile",
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysFile");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "match_all" : {
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "MyFile",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysFile, Note");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "match_all" : {
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "MyFile",
                            "SysFile",
                            "Note"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
    }

    @Test
    public void testConverterEQUALS() {
        String es = toESQueryBuilder("select * FROM SysContent where f1=1");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "term" : {
                              "f1" : {
                                "value" : "1"
                              }
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);

        es = toESQueryBuilder("select * FROM SysContent where f1 != 1");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must_not" : [
                                {
                                  "term" : {
                                    "f1" : {
                                      "value" : "1"
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);

        es = toESQueryBuilder("select * FROM SysContent where f1 <> 1");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must_not" : [
                                {
                                  "term" : {
                                    "f1" : {
                                      "value" : "1"
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);

    }

    @Test
    public void testConverterIN() {
        String es = toESQueryBuilder("select * FROM SysContent where f1 IN (1)");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "terms" : {
                              "f1" : [
                                "1"
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysContent where f1 NOT IN (1, '2', 3)");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must_not" : [
                                {
                                  "terms" : {
                                    "f1" : [
                                      "1",
                                      "2",
                                      "3"
                                    ]
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
    }

    @Test
    public void testConverterLIKE() {
        String es = toESQueryBuilder("select * FROM SysContent where f1 LIKE 'foo%'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "match_phrase_prefix" : {
                          "f1" : {
                            "query" : "foo"
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysContent where f1 LIKE '%Foo%'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "wildcard" : {
                          "f1" : {
                            "wildcard" : "*Foo*"
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysContent where f1 NOT LIKE 'Foo%'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must_not" : [
                                {
                                  "match_phrase_prefix" : {
                                    "f1" : {
                                      "query" : "Foo"
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        // invalid input
        toESQueryBuilder("select * FROM SysContent where f1 LIKE '(foo.*$#@^'");
    }

    @Test
    public void testConverterLIKEWildcard() {
        String es;
        es = toESQueryBuilder("SELECT * FROM SysContent WHERE f1 LIKE '%foo'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "wildcard" : {
                          "f1" : {
                            "wildcard" : "*foo"
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("SELECT * FROM SysContent WHERE f1 LIKE '_foo'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "wildcard" : {
                          "f1" : {
                            "wildcard" : "?foo"
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("SELECT * FROM SysContent WHERE f1 LIKE '?foo'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "wildcard" : {
                          "f1" : {
                            "wildcard" : "\\\\?foo"
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        // * is also accepted as a wildcard (compat)
        es = toESQueryBuilder("SELECT * FROM SysContent WHERE f1 LIKE '*foo'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "wildcard" : {
                          "f1" : {
                            "wildcard" : "*foo"
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        // NXQL escaping
        es = toESQueryBuilder("SELECT * FROM SysContent WHERE f1 LIKE 'foo\\\\_bar\\\\%'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "wildcard" : {
                          "f1" : {
                            "wildcard" : "foo_bar%"
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
    }

    @Test
    public void testConverterILIKE() {
        String es = toESQueryBuilder("select * FROM SysContent where f1 ILIKE 'Foo%'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "match_phrase_prefix" : {
                          "f1.lowercase" : {
                            "query" : "foo"
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysContent where f1 ILIKE '%Foo%'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "wildcard" : {
                          "f1.lowercase" : {
                            "wildcard" : "*foo*"
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysContent where f1 NOT ILIKE 'Foo%'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must_not" : [
                                {
                                  "match_phrase_prefix" : {
                                    "f1.lowercase" : {
                                      "query" : "foo"
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
    }

    @Test
    public void testConverterIsNULL() {
        String es = toESQueryBuilder("select * FROM SysContent where f1 IS NULL");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must_not" : [
                                {
                                  "exists" : {
                                    "field" : "f1"
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysContent where f1 IS NOT NULL");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "exists" : {
                              "field" : "f1"
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
    }

    @Test
    public void testConverterBETWEEN() {
        String es = toESQueryBuilder("select * FROM SysContent where f1 BETWEEN 1 AND 2");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "range" : {
                              "f1" : {
                                "from" : "1",
                                "to" : "2"
                              }
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysContent where f1 NOT BETWEEN 1 AND 2");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must_not" : [
                                {
                                  "range" : {
                                    "f1" : {
                                      "from" : "1",
                                      "to" : "2"
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
    }

    @Test
    public void testConverterSTARTSWITH() {
        String es = toESQueryBuilder("select * FROM SysContent where sys_path STARTSWITH '/the/path'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must" : [
                                {
                                  "term" : {
                                    "sys_path.children" : {
                                      "value" : "/the/path"
                                    }
                                  }
                                }
                              ],
                              "must_not" : [
                                {
                                  "term" : {
                                    "sys_path" : {
                                      "value" : "/the/path"
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysContent where sys_path STARTSWITH '/'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "exists" : {
                              "field" : "sys_parentId"
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysContent where sys_path STARTSWITH '/the/path/'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must" : [
                                {
                                  "term" : {
                                    "sys_path.children" : {
                                      "value" : "/the/path"
                                    }
                                  }
                                }
                              ],
                              "must_not" : [
                                {
                                  "term" : {
                                    "sys_path" : {
                                      "value" : "/the/path/"
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        // for other field than sys_path we want to match the root
        es = toESQueryBuilder("select * from SysContent where mydc_coverage STARTSWITH 'Europe/France'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "term" : {
                              "mydc_coverage.children" : {
                                "value" : "Europe/France"
                              }
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
    }

    @Test
    public void testConverterNOW() {
        // force NXQL.nowPlusPeriodAndDuration to return a known date/time, for tests
        Framework.getProperties().put(HXQL.TEST_HXQL_NOW, "2001-02-03T04:05:06.007Z");
        String es = toESQueryBuilder("SELECT * FROM SysContent WHERE f1 = NOW()");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "term" : {
                              "f1" : {
                                "value" : "2001-02-03T04:05:06.007Z"
                              }
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("SELECT * FROM SysContent WHERE f1 = NOW('-P1D')");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "term" : {
                              "f1" : {
                                "value" : "2001-02-02T04:05:06.007Z"
                              }
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        Framework.getProperties().remove(HXQL.TEST_HXQL_NOW);
    }

    @Test
    public void testConverterAncestorId() {
        String es = toESQueryBuilder(
                "select * FROM SysContent where sys_ancestorIds = 'c5904f77-299a-411e-8477-81d3102a81f9'");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "exists" : {
                              "field" : "ancestorid-without-session"
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder(
                "select * FROM SysContent where sys_ancestorIds != 'c5904f77-299a-411e-8477-81d3102a81f9'", session);
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must_not" : [
                                {
                                  "exists" : {
                                    "field" : "ancestorid-not-found"
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
    }

    @Test
    public void testConverterIsVersionTrue() {
        String expected = """
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "term" : {
                              "sysver_isVersion" : {
                                "value" : true
                              }
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """;
        // legacy int form
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isVersion = 1"));
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isVersion <> 0"));
        // boolean literal form
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isVersion = TRUE"));
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isVersion <> FALSE"));
    }

    @Test
    public void testConverterIsVersionFalse() {
        String expected = """
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must_not" : [
                                {
                                  "exists" : {
                                    "field" : "sysver_isVersion"
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """;
        // legacy int form
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isVersion = 0"));
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isVersion <> 1"));
        // boolean literal form
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isVersion = FALSE"));
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isVersion <> TRUE"));
    }

    @Test
    public void testConverterIsCheckedInTrue() {
        String expected = """
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "term" : {
                              "sysver_isCheckedIn" : {
                                "value" : true
                              }
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """;
        // legacy int form
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isCheckedIn = 1"));
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isCheckedIn <> 0"));
        // boolean literal form
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isCheckedIn = TRUE"));
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isCheckedIn <> FALSE"));
    }

    @Test
    public void testConverterIsCheckedInFalse() {
        String expected = """
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must_not" : [
                                {
                                  "exists" : {
                                    "field" : "sysver_isCheckedIn"
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """;
        // legacy int form
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isCheckedIn = 0"));
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isCheckedIn <> 1"));
        // boolean literal form
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isCheckedIn = FALSE"));
        assertEqualsEvenUnderWindows(expected,
                toESQueryBuilder("select * FROM SysContent where sysver_isCheckedIn <> TRUE"));
    }

    @Test
    public void testConverterFulltext() {
        // Given a search on a fulltext field
        String es = toESQueryBuilder("select * FROM SysContent where sys_fulltext='+foo -bar'");
        // then we have a simple query text and not a filter
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "simple_query_string" : {
                          "query" : "+foo -bar",
                          "fields" : [
                            "all_field"
                          ],
                          "analyzer" : "fulltext",
                          "default_operator" : "and",
                          "analyze_wildcard" : true
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysContent where sys_fulltext_someindex LIKE '+foo -bar'");
        // don't handle nxql fulltext index definition, match to _all field
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "simple_query_string" : {
                          "query" : "+foo -bar",
                          "fields" : [
                            "all_field"
                          ],
                          "analyzer" : "fulltext",
                          "default_operator" : "and",
                          "analyze_wildcard" : true
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysContent where sys_fulltext.sys_title!='+foo -bar'");
        // request on field match field.fulltext
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must_not" : [
                                {
                                  "simple_query_string" : {
                                    "query" : "+foo -bar",
                                    "fields" : [
                                      "sys_title.fulltext"
                                    ],
                                    "analyzer" : "fulltext",
                                    "default_operator" : "and",
                                    "analyze_wildcard" : true
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
    }

    @Test
    public void testConverterFulltextOpenSearchPrefix() {
        // Given a search on a fulltext field with the
        // opensearch-specific prefix
        String es = toESQueryBuilder("SELECT * FROM SysContent WHERE sys_fulltext = 'es: foo bar'");
        // then we have a simple query text and not a filter
        // and we have the OR operator
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "simple_query_string" : {
                          "query" : "foo bar",
                          "fields" : [
                            "all_field"
                          ],
                          "analyzer" : "fulltext",
                          "default_operator" : "or",
                          "analyze_wildcard" : true
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
    }

    @Disabled("trash not implemented for now")
    @Test
    public void testConverterIsTrashedWithProperty() {
        String expectedTrashed = """
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "term" : {
                              "sys_isTrashed" : {
                                "value" : true
                              }
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysMDComment",
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDBookmark",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysMDAnnotation",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """;
        String expectedNotTrashed = """
                {
                  "bool" : {
                    "must" : [
                      {
                        "constant_score" : {
                          "filter" : {
                            "bool" : {
                              "must_not" : [
                                {
                                  "term" : {
                                    "sys_isTrashed" : {
                                      "value" : true
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysMDComment",
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDBookmark",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysMDAnnotation",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """;
        // legacy int form
        assertEqualsEvenUnderWindows(expectedTrashed,
                toESQueryBuilder("SELECT * FROM SysContent WHERE sys_isTrashed = 1"));
        assertEqualsEvenUnderWindows(expectedNotTrashed,
                toESQueryBuilder("SELECT * FROM SysContent WHERE sys_isTrashed = 0"));
        // boolean literal form
        assertEqualsEvenUnderWindows(expectedTrashed,
                toESQueryBuilder("SELECT * FROM SysContent WHERE sys_isTrashed = TRUE"));
        assertEqualsEvenUnderWindows(expectedNotTrashed,
                toESQueryBuilder("SELECT * FROM SysContent WHERE sys_isTrashed = FALSE"));
    }

    @Test
    public void testConverterWhereCombination() {
        String es = toESQueryBuilder("select * FROM SysContent where f1=1 AND f2=2");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "bool" : {
                          "must" : [
                            {
                              "constant_score" : {
                                "filter" : {
                                  "term" : {
                                    "f1" : {
                                      "value" : "1"
                                    }
                                  }
                                }
                              }
                            },
                            {
                              "constant_score" : {
                                "filter" : {
                                  "term" : {
                                    "f2" : {
                                      "value" : "2"
                                    }
                                  }
                                }
                              }
                            }
                          ]
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        es = toESQueryBuilder("select * FROM SysContent where f1=1 OR f2=2");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "bool" : {
                          "should" : [
                            {
                              "constant_score" : {
                                "filter" : {
                                  "term" : {
                                    "f1" : {
                                      "value" : "1"
                                    }
                                  }
                                }
                              }
                            },
                            {
                              "constant_score" : {
                                "filter" : {
                                  "term" : {
                                    "f2" : {
                                      "value" : "2"
                                    }
                                  }
                                }
                              }
                            }
                          ]
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);

        es = toESQueryBuilder("select * FROM SysContent where f1=1 AND f2=2 AND f3=3");
        //  assertEquals("foo", es);

        es = toESQueryBuilder("select * FROM SysContent where f1=1 OR f2=2 OR f3=3");
        //  assertEquals("foo", es);

        es = toESQueryBuilder("select * FROM SysContent where f1=1 OR f2 LIKE 'foo' OR f3=3");
        //  assertEquals("foo", es);

        es = toESQueryBuilder("select * FROM SysContent where (f1=1 OR f2=2) AND f3=3");
        assertEqualsEvenUnderWindows("""
             {
               "bool" : {
                 "must" : [
                   {
                     "bool" : {
                       "must" : [
                         {
                           "bool" : {
                             "should" : [
                               {
                                 "constant_score" : {
                                   "filter" : {
                                     "term" : {
                                       "f1" : {
                                         "value" : "1"
                                       }
                                     }
                                   }
                                 }
                               },
                               {
                                 "constant_score" : {
                                   "filter" : {
                                     "term" : {
                                       "f2" : {
                                         "value" : "2"
                                       }
                                     }
                                   }
                                 }
                               }
                             ]
                           }
                         },
                         {
                           "constant_score" : {
                             "filter" : {
                               "term" : {
                                 "f3" : {
                                   "value" : "3"
                                 }
                               }
                             }
                           }
                         }
                       ]
                     }
                   }
                 ],
                 "filter" : [
                   {
                     "terms" : {
                       "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                     }
                   }
                 ]
               }
             }
                """, es);
    }

    @Test
    public void testConverterComplex() {
        String es = toESQueryBuilder("select * FROM SysContent where (f1 LIKE '1%' OR f2 LIKE '2%') AND f3=3");
        assertEqualsEvenUnderWindows("""
             {
               "bool" : {
                 "must" : [
                   {
                     "bool" : {
                       "must" : [
                         {
                           "bool" : {
                             "should" : [
                               {
                                 "match_phrase_prefix" : {
                                   "f1" : {
                                     "query" : "1"
                                   }
                                 }
                               },
                               {
                                 "match_phrase_prefix" : {
                                   "f2" : {
                                     "query" : "2"
                                   }
                                 }
                               }
                             ]
                           }
                         },
                         {
                           "constant_score" : {
                             "filter" : {
                               "term" : {
                                 "f3" : {
                                   "value" : "3"
                                 }
                               }
                             }
                           }
                         }
                       ]
                     }
                   }
                 ],
                 "filter" : [
                   {
                     "terms" : {
                       "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                     }
                   }
                 ]
               }
             }
                """, es);
        //  assertEquals("foo", es);
        es = toESQueryBuilder(
                "select * FROM SysContent where sys_fulltext='foo bar' AND sys_path STARTSWITH '/foo/bar' OR sys_path='/foo/'");
        //  assertEquals("foo", es);

        es = toESQueryBuilder("select * FROM SysFile, Note where f1 IN ('foo', 'bar', 'foo') AND NOT f2>=3");
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "bool" : {
                          "must" : [
                            {
                              "constant_score" : {
                                "filter" : {
                                  "terms" : {
                                    "f1" : [
                                      "foo",
                                      "bar",
                                      "foo"
                                    ]
                                  }
                                }
                              }
                            },
                            {
                              "bool" : {
                                "must_not" : [
                                  {
                                    "constant_score" : {
                                      "filter" : {
                                        "range" : {
                                          "f2" : {
                                            "gte" : "3"
                                          }
                                        }
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                          ]
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "MyFile",
                            "SysFile",
                            "Note"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
    }

    @Test
    public void testConverterWhereWithoutSelect() {
        String es = toESQueryBuilder("f1=1");
        assertEqualsEvenUnderWindows("""
             {
               "bool" : {
                 "must" : [
                   {
                     "constant_score" : {
                       "filter" : {
                         "term" : {
                           "f1" : {
                             "value" : "1"
                           }
                         }
                       }
                     }
                   }
                 ],
                 "filter" : [
                   {
                     "terms" : {
                       "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                     }
                   }
                 ]
               }
             }
             """, es);
        es = toESQueryBuilder(null);
        assertEqualsEvenUnderWindows("""
              {
                "bool" : {
                  "must" : [
                    {
                      "match_all" : {
                      }
                    }
                  ],
                  "filter" : [
                    {
                      "terms" : {
                        "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                      }
                    }
                  ]
                }
              }
              """, es);
        es = toESQueryBuilder("");
        assertEqualsEvenUnderWindows("""
           {
             "bool" : {
               "must" : [
                 {
                   "match_all" : {
                   }
                 }
               ],
               "filter" : [
                 {
                   "terms" : {
                     "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                   }
                 }
               ]
             }
           }
           """, es);
    }

    @Test
    public void testConvertComplexProperties() {
        String es = toESQueryBuilder("select * FROM SysContent where sysfile_blob/filename = 'foo'");
        assertEqualsEvenUnderWindows("""
               {
                 "bool" : {
                   "must" : [
                     {
                       "constant_score" : {
                         "filter" : {
                           "term" : {
                             "sysfile_blob.filename" : {
                               "value" : "foo"
                             }
                           }
                         }
                       }
                     }
                   ],
                   "filter" : [
                     {
                       "terms" : {
                         "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                       }
                     }
                   ]
                 }
               }
               """, es);
    }

    @Test
    public void testConvertComplexListProperties() {
        String es = toESQueryBuilder("select * from SysContent where mydc_subjects/* = 'foo'");
        // this is supported and match any element of the list
        assertEqualsEvenUnderWindows("""
         {
           "bool" : {
             "must" : [
               {
                 "constant_score" : {
                   "filter" : {
                     "term" : {
                       "mydc_subjects" : {
                         "value" : "foo"
                       }
                     }
                   }
                 }
               }
             ],
             "filter" : [
               {
                 "terms" : {
                   "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                 }
               }
             ]
           }
         }
         """, es);

        es = toESQueryBuilder("select * FROM SysContent where sysfiles_blobs/*/blob/length=123");
        assertEqualsEvenUnderWindows("""
             {
               "bool" : {
                 "must" : [
                   {
                     "constant_score" : {
                       "filter" : {
                         "term" : {
                           "sysfiles_blobs.blob.length" : {
                             "value" : "123"
                           }
                         }
                       }
                     }
                   }
                 ],
                 "filter" : [
                   {
                     "terms" : {
                       "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                     }
                   }
                 ]
               }
             }
             """, es);
        assertEqualsEvenUnderWindows("""
             {
               "bool" : {
                 "must" : [
                   {
                     "constant_score" : {
                       "filter" : {
                         "term" : {
                           "sysfiles_blobs.blob.length" : {
                             "value" : "123"
                           }
                         }
                       }
                     }
                   }
                 ],
                 "filter" : [
                   {
                     "terms" : {
                       "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                     }
                   }
                 ]
               }
             }
             """, es);

    }

    @Test
    public void testConvertComplexListPropertiesUnsupported() {
        String es = toESQueryBuilder("select * from SysContent where mydc_subjects/3 = 'foo'");
        // This is not supported and generate query that is going to match nothing
        assertEqualsEvenUnderWindows("""
             {
               "bool" : {
                 "must" : [
                   {
                     "constant_score" : {
                       "filter" : {
                         "term" : {
                           "mydc_subjects.3" : {
                             "value" : "foo"
                           }
                         }
                       }
                     }
                   }
                 ],
                 "filter" : [
                   {
                     "terms" : {
                       "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                     }
                   }
                 ]
               }
             }
             """, es);

        es = toESQueryBuilder("select * from SysContent where mydc_subjects/*1 = 'foo'");
        // This is not supported and generate query that is going to match nothing
        assertEqualsEvenUnderWindows("""
             {
               "bool" : {
                 "must" : [
                   {
                     "constant_score" : {
                       "filter" : {
                         "term" : {
                           "mydc_subjects1" : {
                             "value" : "foo"
                           }
                         }
                       }
                     }
                   }
                 ],
                 "filter" : [
                   {
                     "terms" : {
                       "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                     }
                   }
                 ]
               }
             }
             """, es);
        es = toESQueryBuilder("select * FROM SysContent where sysfiles_blobs/*1/blob/length=123");
        // This is not supported and generate query that is going to match nothing
        assertEqualsEvenUnderWindows("""
             {
               "bool" : {
                 "must" : [
                   {
                     "constant_score" : {
                       "filter" : {
                         "term" : {
                           "sysfiles_blobs1.blob.length" : {
                             "value" : "123"
                           }
                         }
                       }
                     }
                   }
                 ],
                 "filter" : [
                   {
                     "terms" : {
                       "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                     }
                   }
                 ]
               }
             }
             """, es);

    }

    @Test
    public void testOrderByFromNxql() {
        String nxql = "name='foo' ORDER BY name DESC";
        String es = toESQueryBuilder(nxql);
        assertEqualsEvenUnderWindows("""
             {
               "bool" : {
                 "must" : [
                   {
                     "constant_score" : {
                       "filter" : {
                         "term" : {
                           "name" : {
                             "value" : "foo"
                           }
                         }
                       }
                     }
                   }
                 ],
                 "filter" : [
                   {
                     "terms" : {
                       "sys_primaryType" : [
                            "SysVocabulary",
                            "SysRendition",
                            "MyFile",
                            "SysMDComment",
                            "SysMDBookmark",
                            "SysMDAnnotation",
                            "SysContent",
                            "SysOrderedFolder",
                            "SysRoot",
                            "SysEmbeddings",
                            "SysFolder",
                            "Note",
                            "SysVocabularyEntry",
                            "SysRenditionsContainer",
                            "SysFile"
                          ]
                     }
                   }
                 ]
               }
             }
             """, es);
        OpenSearchQueryBuilder qb = new OpenSearchQueryBuilder(session).hxql(nxql);
        qb.makeQuery();
        assertEquals(1, qb.getSortInfos().size());
        assertEquals("SortInfo [sortColumn=name, sortAscending=false]", qb.getSortInfos().get(0).toString());
    }

    @Test
    public void testOrderByWithComplexProperties() {
        String nxql = "SELECT * FROM SysFile ORDER BY sysfile_blob/filename DESC";
        String es = toESQueryBuilder(nxql);
        assertEqualsEvenUnderWindows("""
                {
                  "bool" : {
                    "must" : [
                      {
                        "match_all" : {
                        }
                      }
                    ],
                    "filter" : [
                      {
                        "terms" : {
                          "sys_primaryType" : [
                            "MyFile",
                            "SysFile"
                          ]
                        }
                      }
                    ]
                  }
                }
                """, es);
        OpenSearchQueryBuilder qb = new OpenSearchQueryBuilder(session).hxql(nxql);
        qb.makeQuery();
        assertEquals(1, qb.getSortInfos().size());
        assertEquals("SortInfo [sortColumn=sysfile_blob.filename, sortAscending=false]",
                qb.getSortInfos().get(0).toString());
    }

    /**
     * A MultiExpression combines its predicates with the operator it carries, rather than adding them to whatever
     * encloses it. Only a tree built in memory has one, since a parsed query nests binary operators instead.
     */
    @Test
    public void testMultiExpression() {
        Predicate f1 = Predicates.eq("f1", "a");
        Predicate f2 = Predicates.eq("f2", "b");
        assertEqualsEvenUnderWindows("""
                {"bool":{"should":[{"constant_score":{"filter":{"term":{"f1":{"value":"a"}}}}},\
                {"constant_score":{"filter":{"term":{"f2":{"value":"b"}}}}}]}}
                """, convert(new MultiExpression(Operator.OR, List.of(f1, f2))));
        // nested under another operator it still keeps its own
        Predicate f3 = Predicates.eq("f3", "c");
        assertEqualsEvenUnderWindows("""
                {"bool":{"must":[{"constant_score":{"filter":{"term":{"f3":{"value":"c"}}}}},\
                {"bool":{"should":[{"constant_score":{"filter":{"term":{"f1":{"value":"a"}}}}},\
                {"constant_score":{"filter":{"term":{"f2":{"value":"b"}}}}}]}}]}}
                """, convert(Predicates.and(f3, new MultiExpression(Operator.OR, List.of(f1, f2)))));
    }

    private String convert(Predicate predicate) {
        SQLQuery query = new SQLQuery(new SelectClause(), new FromClause(), new WhereClause(predicate));
        return toJsonString(nxqlQueryConverter.toESQueryBuilder(query, null).build().toQuery());
    }

    private String toESQueryBuilder(String nxql) {
        return toESQueryBuilder(nxql, null);
    }

    private String toESQueryBuilder(String nxql, CoreSession session) {
        Query query = nxqlQueryConverter.toESQueryBuilder(HXQLQueryConverter.parse(nxql), session)
                                        .build()
                                        .toQuery();
        return toJsonString(query);
    }

    private String toJsonString(JsonpSerializable query) {
        OpenSearchClient client = oss.getClient();
        try (StringWriter writer = new StringWriter()) {
            try (JsonGenerator generator = client.getJsonProvider().createGenerator(writer)) {
                query.serialize(generator, client.getJsonpMapper());
            }
            return writer.toString();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected void assertEqualsEvenUnderWindows(String expected, String actual) {
        try {
            JSONAssert.assertEquals(actual, expected, actual, true);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

}
