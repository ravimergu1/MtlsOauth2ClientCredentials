# mTLS + OAuth2 (Client Credentials) Spring Boot Guide

This document outlines the steps to build, run, and securely connect to this Spring Boot application. This architecture uses a hybrid **Zero-Trust** model: **mTLS** authenticates the machine/transport layer, while **OAuth2 (Client Credentials)** authenticates the application layer.

## 1. Certificate Generation Guide

Run the following commands in your terminal to generate the Root CA, Server, and Client certificates required for the mTLS handshake.

### Step 1: Create the Root CA

This acts as the internal Certificate Authority that will sign and validate both the server and client certificates.

```bash
# 1. Create the CA Private Key
openssl genrsa -out rootCA.key 4096

# 2. Create the Root Certificate (Self-signed)
openssl req -x509 -new -nodes -key rootCA.key -sha256 -days 3650 -out rootCA.pem \
-subj "/C=IN/ST=Telangana/L=Hyderabad/O=Personal/CN=Ravi Dev Root CA"

```

### Step 2: Create the Server Certificates

*(Note: Replace `16.11.225.1` with your actual server IP or Domain Name).*

```bash
# 1. Generate Server Private Key and CSR
openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr \
-subj "/C=IN/ST=Telangana/L=Hyderabad/O=Personal/CN=16.11.225.1"

# 2. Create the SAN Extension File
echo "subjectAltName=IP:16.11.225.1,DNS:localhost" > server-ext.cnf

# 3. Sign the Server Certificate with the Root CA
openssl x509 -req -in server.csr -CA rootCA.pem -CAkey rootCA.key \
-CAcreateserial -out server.crt -days 365 -sha256 \
-extfile server-ext.cnf

# 4. Package into PKCS12 for Spring Boot (Password: changeit)
openssl pkcs12 -export -in server.crt -inkey server.key \
-name "springboot-server" -out server-keystore.p12

```

### Step 3: Create the Client Certificates

*(Note: Use the specific Common Name (CN) that your client represents).*

```bash
# 1. Generate Client Private Key and CSR
openssl genrsa -out client.key 2048
openssl req -new -key client.key -out client.csr \
-subj "/C=IN/ST=Telangana/L=Hyderabad/O=Personal/CN=Ravi-Mac-Client"

# 2. Sign the Client Certificate with the Root CA
openssl x509 -req -in client.csr -CA rootCA.pem -CAkey rootCA.key \
-CAcreateserial -out client.crt -days 365 -sha256

# 3. Package into PKCS12 (Password: changeit)
openssl pkcs12 -export -in client.crt -inkey client.key \
-name "client-identity" -out client-keystore.p12

```

### Step 4: Generate Truststores and JKS Formats

```bash
# 1. Generate truststore.p12 from the Root CA (Password: changeit)
keytool -import -alias "Ravi Dev Root CA" -file rootCA.pem -keystore truststore.p12 -storetype PKCS12

# 2. Convert client-keystore.p12 to JKS format (if required by older client systems)
keytool -importkeystore \
        -srckeystore client-keystore.p12 \
        -srcstoretype PKCS12 \
        -srcstorepass changeit \
        -destkeystore client-keystore.jks \
        -deststoretype JKS \
        -deststorepass changeit

```

---

## 2. Connecting to the Secure API

### For the Spring Boot Server:

Ensure the following files are placed in the `src/main/resources` directory of the application before building:

* `server-keystore.p12` (Proves the server's identity)
* `truststore.p12` (Allows the server to trust incoming client certificates)

### For Client Applications (cURL Testing):

Because this is an OAuth2 architecture, calling the API is a **two-step process**. You must present your mTLS certificates during *both* steps.

**Step A: Fetch the Access Token**
Use your Client ID and Client Secret to request a JWT from the Authorization Server.

```bash
curl -v -k --cert client.crt --key client.key \
     -X POST https://16.11.225.1:8443/oauth2/token \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "grant_type=client_credentials" \
     -d "client_id=my-client-id" \
     -d "client_secret=my-client-secret"

```

*(Copy the `access_token` string from the JSON response).*

**Step B: Call the Secure API Endpoint**
Inject the newly acquired token into the Authorization header to access the Resource Server.

```bash
curl -v -k --cert client.crt --key client.key \
     -H "Authorization: Bearer <PASTE_ACCESS_TOKEN_HERE>" \
     -X GET https://16.11.225.1:8443/api/secure/data

```

### For java application:


1. **Client ID:** `my-client-id`
2. **Client Secret:** `my-client-secret`
3. **OAuth2 Endpoint:** `https://16.11.225.1:8443/oauth2/token`
4. **KeyStore:** Upload `client-keystore.p12` (Password: `changeit`)
5. **TrustStore:** Upload `truststore.p12` (Password: `changeit`)

*Note: Use the generated token as an HTTP Header (`Authorization: Bearer ...`) and not as a URL parameter.*

---

## 3. Build and Run the Spring Boot Application

### Prerequisites

This project requires **Java 17**. Ensure your `JAVA_HOME` is set correctly.
*(Example for macOS using Temurin):*

```bash
export JAVA_HOME=/Users/XXX/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home

```

### Build the Application

Run the following Maven command in the root directory of the project to compile the code and package it into an executable JAR file:

```bash
mvn clean package

```

### Run the Application

Once the build is successful, run the generated JAR file:

```bash
# Run in foreground
java -jar target/mtls-oauth-demo-0.0.1-SNAPSHOT.jar

# OR Run in background (Linux/EC2)
nohup java -jar target/mtls-oauth-demo-0.0.1-SNAPSHOT.jar > application.log 2>&1 &

```
