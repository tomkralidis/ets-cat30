package org.opengis.cite.cat30.opensearch;

import com.sun.jersey.api.client.ClientResponse;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.ws.rs.core.MediaType;
import javax.xml.namespace.QName;
import org.opengis.cite.cat30.CommonFixture;
import org.opengis.cite.cat30.ETSAssert;
import org.opengis.cite.cat30.ErrorMessage;
import org.opengis.cite.cat30.ErrorMessageKeys;
import org.opengis.cite.cat30.Namespaces;
import org.opengis.cite.cat30.SuiteAttribute;
import org.opengis.cite.cat30.util.ClientUtils;
import org.opengis.cite.cat30.util.DatasetInfo;
import org.opengis.cite.cat30.util.OpenSearchTemplateUtils;
import org.opengis.cite.cat30.util.Records;
import org.opengis.cite.cat30.util.ServiceMetadataUtils;
import org.opengis.cite.cat30.util.URIUtils;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Verifies behavior of the SUT when processing queries that contain one or more
 * core OpenSearch parameters. The relevant query parameter bindings are shown
 * in the following table.
 *
 * <table border="1" style="border-collapse: collapse;">
 * <caption>Binding OpenSearch query parameters</caption>
 * <thead>
 * <tr>
 * <th>Parameter name</th>
 * <th>Parameter value template</th>
 * <th>Description</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 * <td>q</td>
 * <td>{searchTerms}</td>
 * <td>A comma-separated list of terms used to search across all text fields.
 * The value must be URL-encoded.</td>
 * </tr>
 * <tr>
 * <td>startPosition</td>
 * <td>{startIndex?}</td>
 * <td>Integer value specifying first search result desired by the client.</td>
 * </tr>
 * <tr>
 * <td>maxRecords</td>
 * <td>{count?}</td>
 * <td>Non-negative integer value specifying the number of search results (per
 * page) desired by the client.</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <h6 style="margin-bottom: 0.5em">Sources</h6>
 * <ul>
 * <li>[OGC-12-176r5] OGC Catalogue Services 3.0 Specification &#x2013; HTTP
 * Protocol Binding, Table 6: KVP encoding for query constraints</li>
 * <li>OpenSearch <a href="http://www.opensearch.org/Specifications/OpenSearch/1.1"
 *  target="_blank">1.1 Draft 5</a></li>
 * </ul>
 */
public class OpenSearchCoreTests extends CommonFixture {

    private String searchTerm;
    private static final QName SEARCH_TERMS_PARAM = new QName(
            Namespaces.OSD11, "searchTerms");
    private Document openSearchDescr;
    private List<Node> templates;
    private List<Node> searchTermsTemplates;
    /**
     * Information about the sample data retrieved from the IUT.
     */
    private DatasetInfo datasetInfo;
    /**
     * A list of record titles retrieved from the IUT.
     */
    private List<String> recordTitles;

    void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }

    /**
     * Initializes the test fixture. A Document representing an OpenSearch
     * description document is obtained from the test context and the URL
     * templates it contains are extracted.
     *
     * @param testContext The test context containing various suite attributes.
     */
    @BeforeClass
    public void initOpenSearchCoreTestsFixture(ITestContext testContext) {
        this.openSearchDescr = (Document) testContext.getSuite().getAttribute(
                SuiteAttribute.OPENSEARCH_DESCR.getName());
        if (null == this.openSearchDescr) {
            throw new SkipException("OpenSearch description not found in test context.");
        }
        this.templates = ServiceMetadataUtils.getOpenSearchURLTemplates(
                this.openSearchDescr);
        QName searchTermsParam = new QName(Namespaces.OSD11, "searchTerms");
        this.searchTermsTemplates = OpenSearchTemplateUtils.filterURLTemplatesByParam(
                templates, searchTermsParam);
        DatasetInfo dataset = (DatasetInfo) testContext.getSuite().getAttribute(
                SuiteAttribute.DATASET.getName());
        if (null == dataset) {
            throw new SkipException("Dataset info not found in test context.");
        }
        this.datasetInfo = dataset;
        this.recordTitles = dataset.getRecordTitles();
    }

    /**
     * [Test] Submits a keyword search where the searchTerms value is a randomly
     * generated sequence of 5-14 characters in the range [a-z]. The result set
     * is expected to be empty; that is, there are no matching entries
     * (os:totalResults = 0).
     */
    @Test(description = "OGC 12-176, Table 6: Text search")
    public void keywordSearch_emptyResultSet() {
        if (this.searchTermsTemplates.isEmpty()) {
            throw new AssertionError("No URL templates containing {searchTerms} parameter.");
        }
        Map<QName, String> values = new HashMap<>();
        values.put(SEARCH_TERMS_PARAM, Records.generateRandomText());
        for (Node template : this.searchTermsTemplates) {
            Element urlElem = (Element) template;
            String mediaType = urlElem.getAttribute("type");
            if (!mediaType.contains("xml")) {
                continue; // ignore non-XML media types
            }
            URI targetURI = OpenSearchTemplateUtils.buildRequestURI(urlElem, values);
            request = ClientUtils.buildGetRequest(targetURI, null,
                    MediaType.valueOf(mediaType));
            response = this.client.handle(request);
            Assert.assertEquals(response.getStatus(),
                    ClientResponse.Status.OK.getStatusCode(),
                    ErrorMessage.get(ErrorMessageKeys.UNEXPECTED_STATUS));
            Document entity = getResponseEntityAsDocument(response, null);
            ETSAssert.assertEmptyResultSet(entity);
        }
    }

    /**
     * [Test] Submits a keyword search where the {searchTerms} value is a title
     * word that occurs in at least one catalog record.
     */
    @Test(description = "OGC 12-176, Table 6: Text search")
    public void singleKeywordSearch() {
        if (this.searchTermsTemplates.isEmpty()) {
            throw new AssertionError("No URL templates containing {searchTerms} parameter.");
        }
        if (null == searchTerm || searchTerm.isEmpty()) {
            int randomIndex = ThreadLocalRandom.current().nextInt(this.recordTitles.size());
            String[] titleWords = this.recordTitles.get(randomIndex).split("\\s+");
            searchTerm = titleWords[titleWords.length - 1];
        }
        Map<QName, String> values = new HashMap<>();
        values.put(SEARCH_TERMS_PARAM, searchTerm);
        for (Node template : this.searchTermsTemplates) {
            Element urlElem = (Element) template;
            NodeList records;
            try {
                records = invokeQuery(urlElem, values);
            } catch (UnsupportedOperationException e) {
                continue; // skip query if it produces non-XML results
            }
            Assert.assertEquals(response.getStatus(),
                    ClientResponse.Status.OK.getStatusCode(),
                    ErrorMessage.get(ErrorMessageKeys.UNEXPECTED_STATUS));
            Assert.assertTrue(records.getLength() > 0,
                    ErrorMessage.format(ErrorMessageKeys.EMPTY_RESULT_SET,
                            Records.getRecordName(urlElem.getAttribute("type"))));
            ETSAssert.assertAllTermsOccur(records, searchTerm);
        }
    }

    /**
     * [Test] Submits a keyword search request where the {searchTerms} value
     * contains two terms. Both terms must occur in each matching record
     * (implicit AND), but case is not significant.
     */
    @Test(description = "OGC 12-176, Table 6: Text search")
    public void multipleKeywordSearch() {
        if (this.searchTermsTemplates.isEmpty()) {
            throw new AssertionError("No URL templates containing {searchTerms} parameter.");
        }
        QName titleName = new QName(Namespaces.DCMES, "title");
        QName subjectName = new QName(Namespaces.DCMES, "subject");
        String searchTerms = Records.findMatchingSearchTerms(
                this.datasetInfo.getDataFile(), titleName, subjectName);
        Map<QName, String> params = new HashMap<>();
        params.put(SEARCH_TERMS_PARAM, URIUtils.getPercentEncodedString(searchTerms));
        for (Node template : this.searchTermsTemplates) {
            Element urlElem = (Element) template;
            NodeList records;
            try {
                records = invokeQuery(urlElem, params);
            } catch (UnsupportedOperationException e) {
                continue; // skip query if it produces non-XML results
            }
            Assert.assertEquals(response.getStatus(),
                    ClientResponse.Status.OK.getStatusCode(),
                    ErrorMessage.get(ErrorMessageKeys.UNEXPECTED_STATUS));
            Assert.assertTrue(records.getLength() > 0,
                    ErrorMessage.format(ErrorMessageKeys.EMPTY_RESULT_SET,
                            Records.getRecordName(urlElem.getAttribute("type"))));
            ETSAssert.assertAllTermsOccur(records, searchTerms.split("\\s+"));
        }
    }

    /**
     * Executes example queries specified in the OpenSearch description
     * document. It is recommended that the document contains at least one query
     * having role="example" in order to allow testing or demonstration of the
     * search service.
     *
     * @see
     * <a target="_blank" href="http://www.opensearch.org/Specifications/OpenSearch/1.1#OpenSearch_Query_element">OpenSearch
     * Query element</a>
     * @see
     * <a target="_blank" href="http://www.opensearch.org/Documentation/Developer_best_practices_guide">Developer
     * best practices guide</a>
     */
    @Test(description = "OpenSearchDescription: Query element")
    public void executeExampleQueries() {
        List<Node> exampleQueryList = ServiceMetadataUtils.getOpenSearchQueriesByRole(
                this.openSearchDescr, new QName(Namespaces.OSD11, "example"));
        if (exampleQueryList.isEmpty()) {
            throw new SkipException("No example queries found in OpenSearch description.");
        }
        for (Node query : exampleQueryList) {
            Map<QName, String> params = OpenSearchTemplateUtils.getQueryParameters(query);
            // Assume all params are allowed in template
            List<Node> qryTemplates = OpenSearchTemplateUtils.filterURLTemplatesByParam(
                    this.templates, params.keySet().iterator().next());
            for (Node template : qryTemplates) {
                Element qryTemplate = (Element) template;
                NodeList records;
                try {
                    records = invokeQuery(qryTemplate, params);
                } catch (UnsupportedOperationException e) {
                    continue; // skip query if it produces non-XML results
                }
                Assert.assertEquals(response.getStatus(),
                        ClientResponse.Status.OK.getStatusCode(),
                        ErrorMessage.get(ErrorMessageKeys.UNEXPECTED_STATUS));
                Assert.assertTrue(records.getLength() > 0,
                        ErrorMessage.format(ErrorMessageKeys.EMPTY_RESULT_SET,
                                Records.getRecordName(qryTemplate.getAttribute("type"))));
            }
        }
    }

    /**
     * Invokes the query defined by the given OpenSearch template. The supplied
     * parameters replace the corresponding substitution variables in the
     * template.
     *
     * @param qryTemplate An Element representing an OpenSearch query template
     * (osd:Url).
     * @param parameters A Map containing the actual query parameters.
     * @return A NodeList containing the records extracted from the response.
     */
    NodeList invokeQuery(Element qryTemplate, Map<QName, String> parameters) {
        String mediaType = qryTemplate.getAttribute("type");
        if (!mediaType.contains("xml")) {
            throw new UnsupportedOperationException(
                    "URL template does not produce XML results: " + mediaType);
        }
        URI targetURI = OpenSearchTemplateUtils.buildRequestURI(qryTemplate, parameters);
        request = ClientUtils.buildGetRequest(targetURI, null,
                MediaType.valueOf(mediaType));
        response = this.client.handle(request);
        Document entity = getResponseEntityAsDocument(response, null);
        QName recordName = Records.getRecordName(mediaType);
        NodeList records = entity.getElementsByTagNameNS(
                recordName.getNamespaceURI(), recordName.getLocalPart());
        return records;
    }
}
