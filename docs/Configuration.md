# Configuration
 
To configure your `DatastoreRepository` you must override the `dataStoreConfiguration` function. You can provide datastore 
configuration using one of the following methods:

## FromEnvironmentVariables
If you implement the method using `override def dataStoreConfiguration = FromEnvironmentVariables` then the following 
environment variables are used to configure the datastore:
 - `DATASTORE_PROJECT_ID` - The ID of the GCP project to connect to. 
 - `DATASTORE_NAMESPACE` - An optional namespace to store your entities under.
 
## DatastoreConfiguration apply Method
You can use `DatastoreConfiguration(projectId)` or `DatastoreConfiguration(projectId, namespace)` to configure your repository.

## Using DatastoreOptions
 
If you need more fine-grained control over your repository then you can create your own [DatastoreOptions](https://googlecloudplatform.github.io/google-cloud-java/0.46.0/apidocs/com/google/cloud/datastore/DatastoreOptions.html) object which
can be implicitly converted into a `DatastoreConfiguration` e.g.
  
  ```scala
import com.ovoenergy.datastore4s.DatastoreConfiguration
import com.google.cloud.datastore.DatastoreOptions
import com.google.auth.oauth2.GoogleCredentials
import com.google.api.core.CurrentMillisClock
import java.io.FileInputStream

val dataStoreConfiguration: DatastoreConfiguration = DatastoreOptions.newBuilder()
  .setProjectId("my-project").setNamespace("my-namespace")
  .setClock(CurrentMillisClock.getDefaultClock())
  .setCredentials(GoogleCredentials.fromStream(new FileInputStream("/secrets/gcp-creds.json")))
  .build()
 ```
 
## Datastore Emulator

For testing purposes it can be useful to use the [Datastore Emulator](https://cloud.google.com/datastore/docs/tools/datastore-emulator).
There are two ways to configure your repository to connect to the emulator, in both cases the credentials will be set to `NoCredentials`
and retry settings will be disabled as per the [notes in the documentation](https://github.com/GoogleCloudPlatform/google-cloud-java/blob/master/TESTING.md#testing-code-that-uses-datastore).

### Explicitly

You can have the configuration to your repository be injected e.g.

```scala
case class MyRepository(dataStoreConfiguration: DatastoreConfiguration) extends DatastoreRepository

// In Production
val repo = MyRepository(DatastoreConfiguration("my-project", "my-namespace"))

// In Test code
val emulatorHost = "localhost:1234"
val testRepo = MyRepository(DatastoreConfiguration("my-test-project", emulatorHost, Some("my-test-namespace")))
```

### Implicitly

By using the environment variable `DATASTORE_EMULATOR_HOST` then any method of configuration will be automatically overridden
to connect to the emulator instead but keeping the other properties the same e.g. projectId.
