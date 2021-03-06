package nl.hsac.fitnesse.fixture;

import fit.exception.FitFailureException;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import nl.hsac.fitnesse.fixture.util.BsnUtil;
import nl.hsac.fitnesse.fixture.util.DatesHelper;
import nl.hsac.fitnesse.fixture.util.FreeMarkerHelper;
import nl.hsac.fitnesse.fixture.util.HttpClient;
import nl.hsac.fitnesse.fixture.util.HttpResponse;
import nl.hsac.fitnesse.fixture.util.NamespaceContextImpl;
import nl.hsac.fitnesse.fixture.util.ProgramHelper;
import nl.hsac.fitnesse.fixture.util.ProgramResponse;
import nl.hsac.fitnesse.fixture.util.RandomUtil;
import nl.hsac.fitnesse.fixture.util.SeleniumHelper;
import nl.hsac.fitnesse.fixture.util.TimeoutHelper;
import nl.hsac.fitnesse.fixture.util.XMLFormatter;
import nl.hsac.fitnesse.fixture.util.XmlHttpResponse;
import org.apache.commons.lang3.StringEscapeUtils;
import util.StringUtil;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds overall environment settings. Expected to be set up before actual tests
 * are performed.
 */
public class Environment {
    private final static Environment INSTANCE = new Environment();
    private Configuration freemarkerConfig;
    private FreeMarkerHelper fmHelper;
    private ConcurrentHashMap<String, Template> templateCache;
    private ConcurrentHashMap<String, String> symbols;
    private HttpClient httpClient;
    private long nextRelationNr = System.currentTimeMillis();
    private NamespaceContextImpl nsContext;
    private XMLFormatter xmlFormatter;
    private BsnUtil bsnUtil = new BsnUtil();
    private RandomUtil randomUtil = new RandomUtil();
    private TimeoutHelper timeoutHelper = new TimeoutHelper();
    private ProgramHelper programHelper;
    private DatesHelper datesHelper = new DatesHelper();
    private SeleniumHelper seleniumHelper;

    private Environment() {
        Configuration cfg = new Configuration();
        // Specify the data source where the template files come from.
        cfg.setClassForTemplateLoading(getClass(), "/templates/");
        DefaultObjectWrapper wrapper = new DefaultObjectWrapper();
        wrapper.setExposeFields(true);
        cfg.setObjectWrapper(wrapper);
        freemarkerConfig = cfg;
        
        fmHelper = new FreeMarkerHelper();
        templateCache = new ConcurrentHashMap<String, Template>();

        symbols = new ConcurrentHashMap<String, String>();

        xmlFormatter = new XMLFormatter();
        nsContext = new NamespaceContextImpl();
        fillNamespaceContext();

        httpClient = new HttpClient();

        programHelper = new ProgramHelper();
        programHelper.setTimeoutHelper(timeoutHelper);
        configDatesHelper();

        seleniumHelper = new SeleniumHelper();
    }

    /**
     * Fills namespace context with default namespaces.
     */
    private void fillNamespaceContext() {
        // SOAP
        registerNamespace("env", "http://schemas.xmlsoap.org/soap/envelope/");
        registerNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
    }

    /**
     * @return singleton instance.
     */
    public static Environment getInstance() {
        return INSTANCE;
    }

    /**
     * @return new instance of class.
     * @throws RuntimeException if no instance could be created.
     */
    public <T> T createInstance(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Unable to create instance of: " + clazz.getName(), e);
        }
    }

    /**
     * Stores key/value to be used.
     * @param key
     * @param value
     */
    public void setSymbol(String key, String value) {
        if (value == null) {
            symbols.remove(key);
        } else {
            symbols.put(key, value);
        }
    }

    /**
     * Retrieves value previously stored.
     * @param key
     * @return value stored for key.
     */
    public String getSymbol(String key) {
        return symbols.get(key);
    }

    /**
     * @return next unique nr
     */
    public long getNextNr() {
        return nextRelationNr++;
    }

    /**
     * Only to be used in unit tests.
     * @param value next number to provide
     */
    public void setNextNr(long value) {
        nextRelationNr = value;
    }

    /**
     * Gets symbol value, or throws exception if no symbol by that key exists.
     * @param key symbol's key.
     * @return symbol's value.
     */
    public String getRequiredSymbol(String key) {
        String result = null;
        Object symbol = getSymbol(key);
        if (symbol == null) {
            throw new FitFailureException("No Symbol defined with key: " + key);
        } else {
            result = symbol.toString();
        }
        return result;
    }

    /**
     * @return FreeMarker configuration to use.
     */
    public Configuration getConfiguration() {
        return freemarkerConfig;
    }

    /**
     * @param name name of template to get
     * @return template by that name
     */
    public Template getTemplate(String name) {
        Template result;
        if (!templateCache.containsKey(name)) {
            Template t = fmHelper.getTemplate(getConfiguration(), name);
            result = templateCache.putIfAbsent(name, t);
            if (result == null) {
                result = t;
            }
        } else {
            result = templateCache.get(name);
        }
        return result;
    }

    /**
     * @param templateName name of template to apply
     * @param model model to supply to template
     * @return result of template
     */
    public String processTemplate(String templateName, Object model) {
        Template t = getTemplate(templateName);
        return fmHelper.processTemplate(t, model);
    }

    /**
     * Performs POST to supplied url of result of applying template with model.
     * All namespaces registered in this environment will be registered with result.
     * @param url url to post to.
     * @param templateName name of template to use.
     * @param model model for template.
     * @param result result to populate with response.
     */
    public void callService(String url, String templateName, Object model, XmlHttpResponse result) {
        doHttpPost(url, templateName, model, result);
        setNamespaceContext(result);
    }

    /**
     * Performs POST to supplied url of result of applying template with model.
     * @param url url to post to.
     * @param templateName name of template to use.
     * @param model model for template.
     * @param result result to populate with response.
     */
    public void doHttpPost(String url, String templateName, Object model, HttpResponse result) {
        doHttpPost(url, templateName, model, result, null);
    }

    /**
     * Performs POST to supplied url of result of applying template with model.
     * @param url url to post to.
     * @param templateName name of template to use.
     * @param model model for template.
     * @param result result to populate with response.
     * @param headers headers to add.
     */
    public void doHttpPost(String url, String templateName, Object model, HttpResponse result, Map<String, String> headers) {
        String request = processTemplate(templateName, model);
        result.setRequest(request);
        doHttpPost(url, result, headers);
    }

    /**
     * Performs POST to supplied url of result's request.
     * @param url url to post to.
     * @param result result containing request, its response will be filled.
     * @param headers headers to add.
     */
    public void doHttpPost(String url, HttpResponse result, Map<String, String> headers) {
        httpClient.post(url, result, headers);
    }

    /**
     * GETs content from URL.
     * @param url url to get from.
     * @return response.
     */
    public HttpResponse doHttpGet(String url) {
        HttpResponse response = new HttpResponse();
        doGet(url, response);
        return response;
    }

    /**
     * GETs XML content from URL.
     * @param url url to get from.
     * @return response.
     */
    public XmlHttpResponse doHttpGetXml(String url) {
        XmlHttpResponse response = new XmlHttpResponse();
        doGet(url, response);
        setNamespaceContext(response);
        return response;
    }

    /**
     * GETs content from URL.
     * @param url url to get from.
     * @param response response to store url and response value in.
     */
    public void doGet(String url, HttpResponse response) {
        response.setRequest(url);
        httpClient.get(url, response);
    }

    private void setNamespaceContext(XmlHttpResponse response) {
        response.setNamespaceContext(getNamespaceContext());
    }

    /**
     * Adds new mapping of prefix to uri for XPath naming resolution.
     * @param prefix prefix that will be used
     * @param uri uri that prefix should refer to.
     */
    public void registerNamespace(String prefix, String uri) {
        nsContext.add(prefix, uri);
    }

    /**
     * @return namespace context for XPath evaluation
     */
    public NamespaceContextImpl getNamespaceContext() {
        return nsContext;
    }

    /**
     * Formats supplied XML string for display in Fitnesse page.
     * @param xmlString XML to format.
     * @return HTML formatted version of xmlString
     */
    public String getHtmlForXml(String xmlString) {
        String result = null;
        if (xmlString != null) {
            if ("".equals(xmlString)) {
                result = "";
            } else {
                String formattedResponse = xmlFormatter.format(xmlString);
                result = "<pre>" + StringEscapeUtils.escapeHtml4(formattedResponse) + "</pre>";
            }
        }
        return result;
    }

    /**
     * Creates exception that will display nicely in a columnFixture.
     * @param msg message for exception
     * @param responseText XML received, which will be shown in wiki table.
     * @throws FitFailureException always
     */
    public static void handleErrorResponse(String msg, String responseText) {
        throw new FitFailureException(msg + getInstance().getHtmlForXml(responseText));
    }

    /**
     * @return random BSN.
     */
    public String createRandomBsn() {
        return bsnUtil.generateBsn();
    }

    /**
     * @param length of string to create.
     * @return lowercase string of specified length.
     */
    public String createRandomLowerCaseString(int length) {
        return randomUtil.randomLower(length);
    }

    /**
     * Invokes an external program, waits for it to complete,
     * and returns the result.
     * @param timeout maximum time (in milliseconds) to wait.
     * @param directory working directory for program
     *          (may be null if not important).
     * @param command program to start.
     * @param arguments arguments for program.
     * @return response from program.
     */
    public ProgramResponse invokeProgram(int timeout, String directory, String command,
                                         String... arguments) {
        ProgramResponse result = new ProgramResponse();
        if (directory != null && !StringUtil.isBlank(directory)) {
            result.setDirectory(new File(directory));
        }
        result.setCommand(command);
        result.setArguments(arguments);
        programHelper.execute(result, timeout);
        return result;
    }

    private void configDatesHelper() {
        datesHelper.setDayPattern("%s_dag");
        datesHelper.setMonthPattern("%s_maand");
        datesHelper.setYearPattern("%s_jaar");
    }

    /**
     * @return datesHelper to use.
     */
    public DatesHelper getDatesHelper() {
        return datesHelper;
    }

    /**
     * @return seleniumHelper to use.
     */
    public SeleniumHelper getSeleniumHelper() {
        return seleniumHelper;
    }
}
