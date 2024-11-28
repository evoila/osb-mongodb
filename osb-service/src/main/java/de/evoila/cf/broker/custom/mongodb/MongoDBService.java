package de.evoila.cf.broker.custom.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Johannes Hiemer
 */
public class MongoDBService {

    private static final Logger log = LoggerFactory.getLogger(MongoDBService.class);
    private MongoClient mongoClient;

    /**
     * Load CA certificate from the file into the Trust Store.
     * Use PKCS12 keystore storing the Client certificate and read it into the {@link KeyStore}
     * Generate {@link SSLContext} from the Trust Store and {@link KeyStore}
     * <p>
     * MongoClientSettings settings = MongoClientSettings.builder()
     * .applyConnectionString(new ConnectionString(connectionString))
     * .applyToSslSettings(builder -> builder.context(sslContext))
     * .build();
     * MongoClient client = MongoClients.create(settings);
     *
     * @return SSLContext
     */
    private static SSLContext createSSLContext(String strCaCert, String clientKeyCert) throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {
        //String certsPath = System.getProperty("cert_path");

        // Path to the CA certificate on disk
        // String caCertPath = certsPath + "/ca.crt";

        // Path to the PKCS12 Key Store holding the Client certificate
        // String clientCertPath = certsPath + "/client.keystore.pkcs12";
        // String clientCertPwd  = "123456";
        SSLContext sslContext;

        try {
            KeyManager[] keyManagers = null;
            if (clientKeyCert != null) {
                InputStream clientInputStream = new ByteArrayInputStream(clientKeyCert.getBytes());
                // Read Client certificate from PKCS12 Key Store
                KeyStore clientKS = KeyStore.getInstance("PKCS12");
                clientKS.load(clientInputStream, null);

                // Retrieve Key Managers from the Client certificate Key Store
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(clientKS, null);
                keyManagers = kmf.getKeyManagers();
            }

            // Read CA certificate from file and convert it into X509Certificate
            InputStream caInputStream = new ByteArrayInputStream(strCaCert.getBytes());
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            X509Certificate caCert = (X509Certificate) certFactory.generateCertificate(caInputStream);

            KeyStore caKS = KeyStore.getInstance(KeyStore.getDefaultType());
            caKS.load(null);
            caKS.setCertificateEntry("caCert", caCert);

            // Initialize Trust Manager
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(caKS);

            // Create SSLContext. We need Trust Manager only in this use case
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, tmf.getTrustManagers(), null);
            return sslContext;
        } catch (Exception ex) {
            log.warn(ex.getMessage());
        }
        return null;
    }

    public boolean isConnected() {
        return mongoClient != null && mongoClient.listDatabases() != null;
    }

    public MongoClient mongoClient() {
        return mongoClient;
    }

    public void createConnection(String username, String password, String database, List<de.evoila.cf.broker.model.catalog.ServerAddress> hosts) {
        createConnection(username, password, database, hosts, null, null);
    }

    public void createConnection(String username, String password, String database, List<de.evoila.cf.broker.model.catalog.ServerAddress> hosts, String caCert, String clientKeyCert) {
        if (database == null) database = "admin";

        List<ServerAddress> serverAddresses = new ArrayList<>();
        for (de.evoila.cf.broker.model.catalog.ServerAddress host : hosts) {
            serverAddresses.add(new ServerAddress(host.getIp(), host.getPort()));
        }

        MongoCredential mongoCredential = MongoCredential.createScramSha1Credential(username, database, password.toCharArray());
        mongoClient = MongoClients.create(MongoClientSettings.builder()
                .applyToClusterSettings(builder -> builder.hosts(serverAddresses))
                .credential(mongoCredential)
                .applyToSslSettings(b -> {
                    try {
                        SSLContext ssl = createSSLContext(caCert, clientKeyCert);
                        if (ssl != null) {
                            b.enabled(true);
                            b.context(ssl);
                        }
                    } catch (IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException |
                             UnrecoverableKeyException | KeyManagementException e) {
                        log.warn(e.getMessage());
                        throw new RuntimeException(e);
                    }
                }).build());
    }

    public void close() {
        mongoClient.close();
    }
}
