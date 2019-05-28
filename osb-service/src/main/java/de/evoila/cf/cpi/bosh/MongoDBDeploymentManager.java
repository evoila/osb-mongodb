package de.evoila.cf.cpi.bosh;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.broker.util.MapUtils;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;
import de.evoila.cf.security.credentials.CredentialStore;
import de.evoila.cf.security.credentials.DefaultCredentialConstants;
import de.evoila.cf.security.utils.RandomString;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Yannic Remmet, Johannes Hiemer.
 */
public class MongoDBDeploymentManager extends DeploymentManager {

    public static final String DATA_DIR = "datadir";

    public static final String REPLICA_SET_NAME = "replica-set-name";

    public static final String VERSION = "version";

    private CredentialStore credentialStore;

    public MongoDBDeploymentManager(BoshProperties boshProperties, Environment environment,
                                    CredentialStore credentialStore) {
        super(boshProperties, environment);
        this.credentialStore = credentialStore;
    }

    @Override
    protected void replaceParameters(ServiceInstance serviceInstance, Manifest manifest, Plan plan,
                                     Map<String, Object> customParameters, boolean isUpdate) {
        HashMap<String, Object> properties = new HashMap<>();
        if (customParameters != null && !customParameters.isEmpty())
            properties.putAll(customParameters);

        log.debug("Updating Deployment Manifest, replacing parameters");

        if (!isUpdate) {
            HashMap<String, Object> manifestProperties = (HashMap<String, Object>) manifest.getInstanceGroups().stream().findAny().get().getProperties();
            HashMap<String, Object> mongodb_exporter = (HashMap<String, Object>) manifestProperties.get("mongodb_exporter");
            HashMap<String, Object> mongodb = (HashMap<String, Object>) manifestProperties.get("mongodb");
            HashMap<String, Object> auth = (HashMap<String, Object>) mongodb.get("auth");
            HashMap<String, Object> replset = (HashMap<String, Object>) auth.get("replica-set");
            HashMap<String, Object> backupAgent = (HashMap<String, Object>) manifestProperties.get("backup_agent");

            if (replset == null)
                auth.put("replica-set", new HashMap<>());

            UsernamePasswordCredential usernamePasswordCredential = credentialStore.createUser(serviceInstance, CredentialConstants.ROOT_CREDENTIALS, "admin");
            serviceInstance.setUsername(usernamePasswordCredential.getUsername());

            usernamePasswordCredential = credentialStore.getUser(serviceInstance, CredentialConstants.ROOT_CREDENTIALS);
            credentialStore.createUser(serviceInstance, DefaultCredentialConstants.BACKUP_CREDENTIALS,
                    usernamePasswordCredential.getUsername(), usernamePasswordCredential.getPassword());

            UsernamePasswordCredential backupAgentusernamePasswordCredential = credentialStore.createUser(serviceInstance,
                    DefaultCredentialConstants.BACKUP_AGENT_CREDENTIALS);
            backupAgent.put("user", backupAgentusernamePasswordCredential.getUsername());
            backupAgent.put("password", backupAgentusernamePasswordCredential.getPassword());

            mongodb_exporter.put("user", usernamePasswordCredential.getUsername());
            mongodb_exporter.put("password", usernamePasswordCredential.getPassword());
            auth.put("user", usernamePasswordCredential.getUsername());
            auth.put("password", usernamePasswordCredential.getPassword());

            if (!replset.containsKey("keyfile")) {
                replset.put("keyfile", new RandomString(1024).nextString());
            }

            if (!properties.containsKey(REPLICA_SET_NAME)) {
                properties.put(REPLICA_SET_NAME, "repSet");
            }

            replset.put("name", properties.get(REPLICA_SET_NAME));
            serviceInstance.getParameters().put("replicaSet", properties.get(REPLICA_SET_NAME));

            if (properties.containsKey(DATA_DIR)) {
                mongodb.put(DATA_DIR, properties.get(DATA_DIR));
            }

            if (properties.containsKey(VERSION)) {
                mongodb.put(VERSION, properties.get(VERSION));

            }
        } else if (isUpdate && customParameters != null && !customParameters.isEmpty()) {
            for (Map.Entry parameter : customParameters.entrySet()) {
                Map<String, Object> manifestProperties = manifestProperties(parameter.getKey().toString(), manifest);

                if (manifestProperties != null)
                    MapUtils.deepMerge(manifestProperties, customParameters);
            }

        }

        this.updateInstanceGroupConfiguration(manifest, plan);
    }

    private Map<String, Object> manifestProperties(String instanceGroup, Manifest manifest) {
        return manifest
                .getInstanceGroups()
                .stream()
                .filter(i -> {
                    if (i.getName().equals(instanceGroup))
                        return true;
                    return false;
                }).findFirst().get().getProperties();
    }

}
