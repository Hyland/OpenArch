# Default Users

OpenArch comes with a set of default users configured in `openarch-users.yml`. These users are available when you run the application locally for development and testing.

## Default Users

| Username | Password | Role | Email |
|----------|----------|------|-------|
| `admin` | `password` | Administrator | admin@example.com |
| `testuser` | `password` | Administrator | test.user@example.com |
| `myUserNameOne` | `password` | Member | firstOne.lastnameOne@example.com |
| `myUserNameTwo` | `password` | Member | firstTwo.lastnameTwo@example.com |
| `testuser3` | `password` | Administrator | testuser3@example.com |
| `testuser4` | `password` | Member | testuser4@example.com |

**Note:** The `{noop}` prefix in the password field indicates that passwords are stored in plaintext (not encoded). This is fine for local development but should never be used in production.

## Role Scopes

### Administrators
- Full system access
- Create, read, write, and delete documents
- Manage user permissions and security policies
- Manage document retention and lifecycle
- Manage locks on documents
- Access all system features and APIs

### Members
- Standard user access
- Create, read, and write documents
- Delete own documents (subject to inherited permissions)
- Cannot manage system-level security or retention policies
- Limited to operations allowed by document-level permissions

## Changing the Admin Username and Password

To customize the admin user credentials:

1. **Locate the users file:**
   ```
   community/server/openarch-app/src/main/resources/openarch-users.yml
   ```

2. **Edit the admin user entry:**
   ```yaml
   - id: "00000000-0000-0000-0000-000000000001"
     username: your-new-admin-username
     password: "{noop}your-new-password"
     firstName: Admin
     lastName: User
     email: admin@example.com
     groups:
       - administrators
       - members
   ```

3. **Rebuild the application:**
   ```bash
   mvn clean install
   ```

4. **Restart OpenArch:**

   **Option A: Running with Docker (using a custom local image)**

   After modifying `openarch-users.yml`, build a local Docker image with your changes:
   ```bash
   mvn clean package
   docker build -f server/openarch-app/Dockerfile -t openarch-app:local target/openarch-app-*.jar
   ```

   Then update your `docker-compose.yml` to use the local image instead of the remote one:
   ```yaml
   services:
     openarch-app:
       image: openarch-app:local
       # ... rest of the configuration
   ```

   Start OpenArch with:
   ```bash
   docker-compose up
   ```

   **Option B: Running standalone (without Docker)**

   ```bash
   java -jar target/openarch-app-*.jar
   ```

Your new credentials will take effect immediately after the restart.

## Adding New Users

To add a new user, add an entry to the `users` section in `openarch-users.yml`:

```yaml
- id: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
  username: new-user
  password: "{noop}their-password"
  firstName: John
  lastName: Doe
  email: john.doe@example.com
  groups:
    - members
```

**Important:** Always use a unique UUID for each user. You can generate one using an online UUID generator or any UUID tool.

### Group Assignment

Users can be assigned to the following groups:

- `administrators` — Full system access
- `members` — Standard user access

After making changes, rebuild and restart the application following the steps in the "Changing the Admin Username and Password" section above (whether you're using Docker or running standalone).

## User Attributes (Optional)

You can add custom attributes to users for additional metadata:

```yaml
- id: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
  username: employee
  password: "{noop}password"
  firstName: Jane
  lastName: Smith
  email: jane.smith@example.com
  groups:
    - members
  attributes:
    YOJ: "2023"
    empId: E123456
    department: Engineering
```

These attributes are available for querying and filtering but don't affect authentication or authorization.
