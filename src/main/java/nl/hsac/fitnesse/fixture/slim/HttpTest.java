package nl.hsac.fitnesse.fixture.slim;

import freemarker.template.Template;
import nl.hsac.fitnesse.fixture.util.HttpResponse;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fixture to make HTTP requests using Slim scripts and/or scenarios.
 */
public class HttpTest extends SlimFixture {
    private final Map<String, Object> currentValues = new LinkedHashMap<String, Object>();
    private final Map<String, String> headerValues = new LinkedHashMap<String, String>();
    private HttpResponse response = createResponse();
    private String template;

    /**
     * Sets template to use.
     * @param aTemplate name of template file (relative to 'templates' directory on classpath)
     * @return true if template could be found.
     */
    public boolean template(String aTemplate) {
        boolean result = false;
        Template t = getEnvironment().getTemplate(aTemplate);
        if (t != null) {
            template = aTemplate;
            result = true;
        }
        return result;
    }

    /**
     * Stores value to be passed to template.
     * @param value value to be passed.
     * @param name name to use this value for.
     */
    public void setValueFor(String value, String name) {
        currentValues.put(name, value);
    }

    /**
     * Clears a values previously set.
     * @param name value to remove.
     * @return true if value was present.
     */
    public boolean clearValue(String name) {
        boolean result = currentValues.containsKey(name);
        currentValues.remove(name);
        return result;
    }

    /**
     * Clears all values previously set.
     */
    public void clearValues() {
        currentValues.clear();
    }

    /**
     * Stores value to be passed as headers.
     * @param value value to be passed.
     * @param name name to use this value for.
     */
    public void setValueForHeader(String value, String name) {
        headerValues.put(name, getUrl(value));
    }

    /**
     * Clears a header value previously set.
     * @param name value to remove.
     * @return true if value was present.
     */
    public boolean clearHeaderValue(String name) {
        boolean result = headerValues.containsKey(name);
        headerValues.remove(name);
        return result;
    }

    /**
     * Clears all header values previously set.
     */
    public void clearHeaderValues() {
        currentValues.clear();
    }

    /**
     * Sends HTTP POST template with current values to service endpoint.
     * @param serviceUrl service endpoint to send XML to.
     * @return true if call could be made and response did not indicate error.
     */
    public boolean postTemplateTo(String serviceUrl) {
        boolean result;
        response = createResponse();
        if (template == null) {
            throw new StopTestException("No template available to use in post");
        } else {
            String url = getUrl(serviceUrl);
            try {
                getEnvironment().doHttpPost(url, template, currentValues, response, headerValues);
            } catch (Throwable t) {
                throw new StopTestException("Unable to get response from POST to: " + url, t);
            }
            result = postProcessResponse();
        }
        return result;
    }

    /**
     * Sends HTTP POST body to service endpoint.
     * @param body content to post
     * @param serviceUrl service endpoint to send XML to.
     * @return true if call could be made and response did not indicate error.
     */
    public boolean postTo(String body, String serviceUrl) {
        boolean result;
        response = createResponse();
        String cleanedBody = cleanupBody(body);
        response.setRequest(cleanedBody);
        String url = getUrl(serviceUrl);
        try {
            getEnvironment().doHttpPost(url, response, headerValues);
        } catch (Throwable t) {
            throw new StopTestException("Unable to get response from POST to: " + url, t);
        }
        result = postProcessResponse();
        return result;
    }

    protected String cleanupBody(String body) {
        String result = body;
        Pattern preFormatted = Pattern.compile("<pre>\\s*(.*?)\\s*</pre>", Pattern.DOTALL);
        Matcher matcher = preFormatted.matcher(body);
        if (matcher.matches()) {
            String escapedBody = matcher.group(1);
            result = StringEscapeUtils.unescapeHtml4(escapedBody);
        }
        return result;
    }

    /**
     * Sends HTTP GET to service endpoint to retrieve XML.
     * @param serviceUrl service endpoint to send XML to.
     * @return true if call could be made and response did not indicate error.
     */
    public boolean getFrom(String serviceUrl) {
        boolean result;
        response = createResponse();
        String url = createUrlWithParams(serviceUrl);
        try {
            getEnvironment().doGet(url, response);
        } catch (Throwable t) {
            throw new StopTestException("Unable to GET response from: " + url, t);
        }
        result = postProcessResponse();
        return result;
    }

    String createUrlWithParams(String serviceUrl) {
        String baseUrl = getUrl(serviceUrl);
        boolean isFirst = true;
        if (baseUrl.contains("?") && !baseUrl.endsWith("?")) {
            isFirst = false;
        }

        for (Map.Entry<String, Object> entry : currentValues.entrySet()) {
            if (isFirst) {
                isFirst = false;
                if (!baseUrl.endsWith("?")) {
                    baseUrl += "?";
                }
            } else {
                baseUrl += "&";
            }
            baseUrl += urlEncode(entry.getKey());
            if (entry.getValue() != null) {
                baseUrl += "=" + urlEncode(entry.getValue().toString());
            }
        }
        return baseUrl;
    }

    private String urlEncode(String str) {
        try {
            return URLEncoder.encode(str, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
    }

    /**
     * Performs any post processing directly after retrieving response.
     * @return true if all is well, false otherwise.
     */
    protected boolean postProcessResponse() {
        return responseIsValid();
    }

    /**
     * @return true if response does not indicate an error.
     */
    public boolean responseIsValid() {
        boolean result;
        try {
            response.validResponse();
            result = true;
        } catch (RuntimeException e) {
            result = false;
        }
        return result;
    }

    /**
     * @return request sent last time postTo() was called.
     */
    public String request() {
        return response.getRequest();
    }

    /**
     * @return response received last time postTo() was called.
     */
    public String response() {
        return response.getResponse();
    }

    /**
     * @return HTML response received last time postTo() or get() was called.
     */
    public String htmlResponse() {
        String content = response.getResponse();
        content = "<div>" + content + "</div>";
        return content;
    }

    /**
     * @return http status received last time postTo() was called.
     */
    public int responseStatus() {
        return response.getStatusCode();
    }

    /**
     * @return current values stored.
     */
    protected Map<String, Object> getCurrentValues() {
        return currentValues;
    }

    protected HttpResponse getResponse() {
        return response;
    }

    protected HttpResponse createResponse() {
        return new HttpResponse();
    }
}
