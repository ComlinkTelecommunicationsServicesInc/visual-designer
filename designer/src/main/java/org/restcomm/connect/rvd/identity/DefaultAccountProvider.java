package org.restcomm.connect.rvd.identity;

import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.restcomm.connect.rvd.RvdConfiguration;
import org.restcomm.connect.rvd.restcomm.RestcommAccountInfo;
import org.restcomm.connect.rvd.utils.RvdUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Provides accounts by either quereing Restcomm or accessing cache. It follows the application lifecycle (not creatd per-request)
 * Future support for account caching will be added.
 *
 * The class has been implemented as a singleton and is lazily created because it's not possible to initialize it
 * in RvdInitializationServlet (restcommBaseUrl is not available at that time) :-(.
 *
 * @author Orestis Tsakiridis
 */
public class DefaultAccountProvider implements AccountProvider {

    String restcommUrl = null; // this is initialized lazily. Access it through its private getter.
    private boolean restcommUrlInitialized = false;
    CloseableHttpClient client;
    RvdConfiguration configuration;

    /**
     * This constructor directly initializes restcommUrl without going through RvdConfiguration
     * and UriUtils. Make sure restcommUrl parameter is properly set.
     *
     * @param restcommUrl
     * @param client
     */
    public DefaultAccountProvider(String restcommUrl, CloseableHttpClient client) {
        if (restcommUrl == null)
            throw new IllegalStateException("restcommUrl cannot be null");
        this.restcommUrl = sanitizeRestcommUrl(restcommUrl);
        this.restcommUrlInitialized = true;
        this.client = client;
    }


    public DefaultAccountProvider(RvdConfiguration configuration, CloseableHttpClient client) {
        this.configuration = configuration;
        this.client = client;
    }


    private String getRestcommUrl() {
        if (!restcommUrlInitialized) {
            URI uriFromConfig = configuration.getRestcommBaseUri();
            if (uriFromConfig == null)
                throw new IllegalStateException("restcommUrl cannot be null");
            String url = uriFromConfig.toString();
            this.restcommUrl = sanitizeRestcommUrl(url);
            restcommUrlInitialized = true;
        }
        return restcommUrl;
    }

    private String sanitizeRestcommUrl(String restcommUrl) {
        restcommUrl = restcommUrl.trim();
        if (restcommUrl.endsWith("/"))
            return restcommUrl.substring(0,restcommUrl.length()-1);
        return restcommUrl;
    }

    private URI buildAccountQueryUrl(String usernameOrSid) {
        try {
            // TODO url-encode the username
            URI uri = new URIBuilder(getRestcommUrl()).setPath("/restcomm/2012-04-24/Accounts.json/" + usernameOrSid).build();
            return uri;
        } catch (URISyntaxException e) {
            // something really wrong has happened
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves account 'accountName' from restcomm using creds as credentials.
     * If the authentication fails or the account is not found it returns null.
     *
     * TODO we need to treat differently missing accounts and failed authentications.
     *
     */
    @Override
    public RestcommAccountInfo getAccount(String accountName, String authorizationHeader) {
        HttpGet GETRequest = new HttpGet(buildAccountQueryUrl(accountName));
        GETRequest.addHeader("Authorization", authorizationHeader);
        try {
            CloseableHttpResponse response = client.execute(GETRequest);
            if (response.getStatusLine().getStatusCode() == 200 ) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    String accountJson = EntityUtils.toString(entity);
                    Gson gson = new Gson();
                    RestcommAccountInfo accountResponse = gson.fromJson(accountJson, RestcommAccountInfo.class);
                    return accountResponse;
                }
            } else
                return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Something went wrong while retrieving account
        return null;
    }

    @Override
    public RestcommAccountInfo getActiveAccount(String accountName, String authorizationHeader) {
        RestcommAccountInfo response = getAccount(accountName, authorizationHeader);
        // if the account is not active, we need to set success status to false
        if ( !"active".equals(response.getStatus()) ) {
            return null;
        } else
            return response;
    }

    @Override
    public RestcommAccountInfo getActiveAccount(BasicAuthCredentials creds) {
        String header = "Basic " + RvdUtils.buildHttpAuthorizationToken(creds.getUsername(),creds.getPassword());
        return getActiveAccount(creds.getUsername(), header);
    }
}

