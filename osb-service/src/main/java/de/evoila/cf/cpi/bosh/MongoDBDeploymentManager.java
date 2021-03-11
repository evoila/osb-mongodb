package de.evoila.cf.cpi.bosh;

import ch.qos.logback.core.db.dialect.DBUtil;
import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.custom.mongodb.MongoDBUtils;
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

        InstanceGroup instanceGroup =  manifest.getInstanceGroup("mongodb").get();
        JobV2 mongoJob = instanceGroup.getJob("mongodb").get();
        Map<String, Object> mongodb_properties = getProperty(mongoJob.getProperties(),"mongodb");



        List<String> databases= (List<String>) mongodb_properties.get("databases");
        if(databases == null){
            databases = new LinkedList<>();
            mongodb_properties.put("databases",databases);
        }
        databases.add(MongoDBUtils.dbName(serviceInstance.getId()));

        Map<String, Object> auth = getProperty(mongodb_properties, "auth");

        PasswordCredential replicaSetKey = credentialStore.createPassword(serviceInstance,"replicaSetKey", 40);
        Map<String, String> replset = (Map<String,String>) auth.get("replica-set");
        if(replset == null){
            replset = new HashMap<String, String>();
            auth.put("replica-set", replset);
        }
        replset.put("keyfile", replicaSetKey.getPassword());

        replset = (Map<String,String>) mongodb_properties.get("replica-set");
        if(replset == null){
            replset = new HashMap<String, String>();
            mongodb_properties.put("replica-set", replset);
        }
        replset.put("name", serviceInstance.getId().replace("-",""));
        
        if (!isUpdate) {

            UsernamePasswordCredential rootCredential = credentialStore.createUser(serviceInstance, CredentialConstants.ROOT_CREDENTIALS, "admin");
            UsernamePasswordCredential backupCredential = credentialStore.createUser(serviceInstance, CredentialConstants.BACKUP_CREDENTIALS, "backup");
            UsernamePasswordCredential backupAgentCredential = credentialStore.createUser(serviceInstance, CredentialConstants.BACKUP_AGENT_CREDENTIALS, "backup_agent");
            UsernamePasswordCredential exporterCredential = credentialStore.createUser(serviceInstance, CredentialConstants.EXPORTER_CREDENTIALS, "exporter");


            if(credentialStore instanceof DatabaseCredentialsClient){
                JobV2 exportJob = instanceGroup.getJob("mongodb_exporter").get();
                JobV2 backupJob = instanceGroup.getJob("backup-agent").get();
                Map<String, Object> exporter_properties = getProperty(exportJob.getProperties(), "mongodb_exporter");
                Map<String, Object> backup_properties = getProperty(backupJob.getProperties(), "backup_agent");

                if (properties.containsKey("version")){
                    mongodb_properties.put("version", properties.get("version"));
                }

                if (properties.containsKey("config")){
                    Map<String, Object> mdbConfig = getProperty(mongodb_properties, "config");
                    MapUtils.deepMerge(mdbConfig, (Map<String, Object>) properties.get("config"));
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

                backup_properties.put("username", backupAgentCredential.getUsername());
                backup_properties.put("password", backupAgentCredential.getPassword());


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
