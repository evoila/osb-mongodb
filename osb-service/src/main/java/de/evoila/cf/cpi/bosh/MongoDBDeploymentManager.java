package de.evoila.cf.cpi.bosh;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.PasswordCredential;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.broker.util.MapUtils;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import de.evoila.cf.cpi.bosh.deployment.manifest.InstanceGroup;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;
import de.evoila.cf.cpi.bosh.deployment.manifest.instanceGroup.JobV2;
import de.evoila.cf.security.credentials.CredentialStore;
import de.evoila.cf.security.credentials.database.DatabaseCredentialsClient;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Yannic Remmet, Johannes Hiemer.
 */
public class MongoDBDeploymentManager extends DeploymentManager {

    private HashMap<String, Object> createUser(String username, String password){
        HashMap<String,Object> user = new HashMap<>();
        user.put("username", username);
        user.put("password", password);
        return user;
    }

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

            UsernamePasswordCredential rootCredential = credentialStore.createUser(serviceInstance, CredentialConstants.ROOT_CREDENTIALS, "admin");
            UsernamePasswordCredential backupCredential = credentialStore.createUser(serviceInstance, CredentialConstants.BACKUP_AGENT_CREDENTIALS, "backup");
            UsernamePasswordCredential exporterCredential = credentialStore.createUser(serviceInstance, CredentialConstants.EXPORTER_CREDENTIALS, "exporter");
            PasswordCredential replicaSetKey = credentialStore.createPassword(serviceInstance,"replicaSetKey", 40);

            InstanceGroup instanceGroup =  manifest.getInstanceGroup("mongodb").get();

            if(credentialStore instanceof DatabaseCredentialsClient){
                JobV2 mongoJob = instanceGroup.getJob("mongodb").get();
                JobV2 exportJob = instanceGroup.getJob("mongodb_exporter").get();
                JobV2 backupJob = instanceGroup.getJob("backup-agent").get();
                Map<String, Object> mongodb_properties = getProperty(mongoJob.getProperties(),"mongodb");
                Map<String, Object> exporter_properties = getProperty(exportJob.getProperties(), "mongodb_exporter");
                Map<String, Object> backup_properties = getProperty(backupJob.getProperties(), "backup_agent");

                if (properties.containsKey("version")){
                    mongodb_properties.put("version", properties.get("version"));
                }

                if (properties.containsKey("config")){
                    Map<String, Object> mdbConfig = getProperty(mongodb_properties, "config");
                    MapUtils.deepMerge(mdbConfig, (Map<String, Object>) properties.get("config"));
                }

                Map<String, Object> auth = getProperty(mongodb_properties, "auth");

                Map<String, Object> replset = getProperty(auth,"replica-set");
                replset.put("keyfile", replicaSetKey.getPassword());
                if (properties.containsKey("replica-set-name")){
                    replset.put("name", properties.get("replica-set-name"));
                }

                List<HashMap<String, Object>> admins= (List<HashMap<String, Object>>) auth.get("admin_users");
                if(admins == null){
                    admins = new LinkedList<>();
                    auth.put("admin_users",admins);
                }
                admins.clear();
                admins.add(createUser(rootCredential.getUsername(),rootCredential.getPassword()));
                admins.add(createUser(exporterCredential.getUsername(),exporterCredential.getPassword()));

                List<HashMap<String, Object>> backup_users= (List<HashMap<String, Object>>) auth.get("backup_users");
                if(backup_users == null){
                    backup_users = new LinkedList<>();
                    auth.put("backup_users",backup_users);
                }
                backup_users.clear();
                backup_users.add(createUser(backupCredential.getUsername(),backupCredential.getPassword()));

                Map<String, Object> exporter_properties_mongodb = getProperty(exporter_properties,"mongodb");
                exporter_properties_mongodb.put("uri", "mongodb://" + exporterCredential.getUsername() + ":" + exporterCredential.getPassword() + "@127.0.0.1:27017/admin");

                backup_properties.put("username", backupCredential.getUsername());
                backup_properties.put("password", backupCredential.getPassword());
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
