# Local Development Setup

This guide walks through the full local development setup for HAFMessenger.

## Prerequisites

| Tool    | Version | Notes                                      |
| ------- | ------- | ------------------------------------------ |
| Java    | 25+     | JDK with `keytool`                         |
| Maven   | 3.9+    | Wrapper included (`./mvnw`)                |
| MySQL   | 8.0+    | Local instance                             |
| OpenSSL | 3.0+    | For certificate and key generation         |

## Quick Setup

Run the bootstrap script from the project root:

```bash
./scripts/bootstrap-local-dev.sh
```

This creates everything under `.local/hafmessenger/` (gitignored):

| File | Purpose |
| --- | --- |
| `server/variables.env` | Server environment configuration with generated secrets |
| `server/server.p12` | HTTPS TLS keystore (self-signed, SANs: localhost, 127.0.0.1, ::1) |
| `server/mysql-ssl/*` | MySQL CA and server certificates |
| `server/mysql-truststore.p12` | Java truststore for MySQL TLS verification |
| `server/admin_private_key.txt` | Admin X25519 private key for registration photo decryption |
| `client/truststore.p12` | Client-side truststore containing the server certificate |
| `client/client.properties` | Client configuration pointing to localhost |

## Database Setup

### 1. Configure MySQL with Generated Certificates

Copy the generated certificates to MySQL's data directory:

```bash
sudo cp .local/hafmessenger/server/mysql-ssl/ca-cert.pem /var/lib/mysql/ca.pem
sudo cp .local/hafmessenger/server/mysql-ssl/server-cert.pem /var/lib/mysql/server-cert.pem
sudo cp .local/hafmessenger/server/mysql-ssl/server-key.pem /var/lib/mysql/server-key.pem
sudo chown mysql:mysql /var/lib/mysql/ca.pem /var/lib/mysql/server-cert.pem /var/lib/mysql/server-key.pem
sudo chmod 600 /var/lib/mysql/server-key.pem
```

### 2. Configure MySQL SSL

Edit `/etc/mysql/mysql.conf.d/mysqld.cnf`:

```ini
[mysqld]
ssl_ca   = /var/lib/mysql/ca.pem
ssl_cert = /var/lib/mysql/server-cert.pem
ssl_key  = /var/lib/mysql/server-key.pem
```

Restart MySQL:

```bash
sudo systemctl restart mysql
```

### 3. Create Database and User

Get the generated DB password from `.local/hafmessenger/server/variables.env` (`HAF_DB_PASS`), then:

```sql
CREATE DATABASE IF NOT EXISTS haf_messenger;
CREATE USER 'haf_app'@'localhost' IDENTIFIED BY '<password>';
GRANT ALL PRIVILEGES ON haf_messenger.* TO 'haf_app'@'localhost';
FLUSH PRIVILEGES;
```

> **Tip**: If you want to skip MySQL SSL for local development, change `sslMode=VERIFY_IDENTITY` to `sslMode=DISABLED` in `variables.env` and remove the `HAF_DB_TRUSTSTORE_*` entries.

## Running the Server

### Option A: From Source (Development)

```bash
./mvnw -pl server exec:java
```

*(On Windows command prompt, use `.\mvnw.cmd -pl server exec:java`)*

### Option B: Standalone JAR (Production & Easy Execution)

You can compile the server into a single executable standalone JAR file containing all dependencies:

1. Package the server:

   ```bash
   ./mvnw -pl server clean package -DskipTests
   ```

   *(Creates the file `server/target/HAFMessengerServer.jar`)*

2. Run it on any OS (Linux/Mac/Windows) with Java installed:

   ```bash
   java -jar server/target/HAFMessengerServer.jar
   ```

   *(Alternatively, on Windows or Mac, if your system has `.jar` files associated with the Java Runtime, you can simply **double-click** the `HAFMessengerServer.jar` file to run the server).*

The server starts HTTPS on port `8443` and WSS on port `8444`.

## Running the Client

Copy the generated client config to the expected resource locations:

```bash
cp .local/hafmessenger/client/client.properties client/src/main/resources/config/
cp .local/hafmessenger/client/truststore.p12 client/src/main/resources/config/
```

Then run:

```bash
./mvnw -pl client javafx:run
```

## Running Tests

```bash
# All modules (shared + server; client tests require JavaFX display)
./mvnw test

# Server tests only
HAF_SERVER_ENV_FILE=.local/hafmessenger/server/variables.env ./mvnw -pl server test

# Shared module only
./mvnw -pl shared test
```

## Regenerating Secrets

To regenerate everything from scratch:

```bash
FORCE=1 ./scripts/bootstrap-local-dev.sh
```

To regenerate individual components:

```bash
# MySQL certificates only
./scripts/generate-mysql-certs.sh

# Server TLS keystore only
./scripts/generate-server-tls.sh

# Admin keypair only
./scripts/generate-admin-keys.sh
```

## Troubleshooting

### "Missing required environment variable" on server start

The server couldn't find `variables.env`. Either:

- Run `./scripts/bootstrap-local-dev.sh` first
- Set `HAF_SERVER_ENV_FILE` to point to your env file

### MySQL SSL verification fails

Ensure:

1. MySQL is configured with the generated certificates
2. MySQL has been restarted after certificate changes
3. The truststore password in `variables.env` matches what was generated

### Client can't connect to server

Ensure:

1. The server is running
2. Client `truststore.p12` was generated from the same server keystore
3. URLs in `client.properties` match the server's listen addresses

## Testing Over the Internet (Optional)

If you want to test the client-server interaction across different networks (e.g. connecting a remote client to your local server), you need to expose your local port `8443` to the internet.

### Option A: Using Microsoft Dev Tunnels (Recommended)

1. Install the CLI tool from Microsoft: [Dev Tunnels installation guide](https://learn.microsoft.com/en-us/azure/developer/dev-tunnels-cli).
2. Host a secure tunnel targeting the server port:

   ```bash
   devtunnel host -p 8443 --allow-anonymous
   ```

3. Copy the public URL generated (e.g., `https://xyz-8443.use.devtunnels.ms`).
4. Update `client.properties` inside your client resource folder to point to the new URL:

   ```properties
   server.url=https://xyz-8443.use.devtunnels.ms/api/v1
   ```

### Option B: Using ngrok

1. Install and authenticate [ngrok](https://ngrok.com/).
2. Run ngrok to tunnel HTTPS traffic to port `8443`:

   ```bash
   ngrok http https://localhost:8443
   ```

3. Copy the public forwarding address.
4. Update `client.properties` in your client resources to use this address:

   ```properties
   server.url=https://<your-ngrok-subdomain>.ngrok-free.app/api/v1
   ```
