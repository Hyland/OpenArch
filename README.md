# Hyland OpenArch
[![Continuous Integration](https://github.com/Hyland/OpenArch/actions/workflows/continuous_integration.yaml/badge.svg?branch=main)](https://github.com/Hyland/OpenArch/actions/workflows/continuous_integration.yaml?query=branch%3Amain)

## What is Hyland OpenArch?
Teams adopting AI typically struggle with data silos, keeping permissions respected at all times, and operating at scale.

Hyland OpenArch is an open source project that:
* Federates unstructured AI ready data coming from all sources across the organization
* Makes it available to AI services and agents while always respecting permissions
* Can be self-managed and used with your own LLMs / choice of AI services

By doing so, it lets teams de-risk their AI projects while setting up solid foundations for data AI readiness at scale.

## Features
### Store and Index Unstructured Data Across Your Organization
* Connect to all your unstructured data sources like Alfresco, Nuxeo, Microsoft SharePoint and others to eliminate AI data silos
* Integrate Hyland OpenArch with your data curation pipeline and store your AI ready data: metadata, chunks, vectors
* Prioritize indexing over replication to prevent storage cost inflation
* Natively replicate permissions from your unstructured data using ACLs instead of focusing on security at the database level

### Use your AI Ready Data With Any AI Service While Respecting Permissions
* An API first design to expose your data to your choice of LLMs and AI services
* Retrieve your data while respecting permissions, security comes by design

### Create a Federated Search Layer for Your Organization
* Simple, keyword based queries
* Advanced queries with filters
* Semantic search (a.k.a. vector search)

See [Hyland OpenArch's documentation](https://hyland.github.io/ContentIntelligence-Docs/) to learn more.

## Quick Start

### Start Hyland OpenArch with Docker
Hyland OpenArch is available as a Docker image. You can start it with the following command, using the version 1.0.0 as an example:
```shell
export OPENARCH_VERSION=1.0.0
docker-compose up
```

### Access Hyland OpenArch
Once Hyland OpenArch is running, you can access the API endpoint list at [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html).

#### Create a New Document
The following command creates a new document in Hyland OpenArch. The document will be created at the root of the repository with the `sys_primaryType` of `SysFolder` and a title of `My Test Folder`. You can change the values in the JSON payload to create a different type of document or set different metadata.
```shell
curl --request POST \
  --user admin:password \
  --url http://localhost:8080/api/documents/00000000-0000-0000-0000-000000000000 \
  --header 'Content-Type: application/json' \
  --data '{
    "sys_primaryType": "SysFolder",
    "sys_title": "My Test Folder"
  }'
```
The response will be a JSON object containing the details of the created document, including its unique identifier (UUID) and other metadata.
```json
{
    "sys_ancestorIds": [
        "00000000-0000-0000-0000-000000000000"
    ],
    "sys_changeToken": "0-0",
    "sys_contributors": [
        {
            "id": "00000000-0000-0000-0000-000000000001",
            "firstName": "Admin",
            "lastName": "User",
            "username": "admin",
            "email": "admin@example.com"
        }
    ],
    "sys_created": "2026-09-01T12:08:35.540734146Z",
    "sys_creator": {
        "id": "00000000-0000-0000-0000-000000000001",
        "firstName": "Admin",
        "lastName": "User",
        "username": "admin",
        "email": "admin@example.com"
    },
    "sys_effectivePermissions": [
        "CreateChild",
        "CreateVersion",
        "Delete",
        "DeleteChild",
        "ManageLock",
        "ManageRetention",
        "ManageSecurity",
        "Read",
        "ReadVersion",
        "ReadWrite",
        "Write",
        "WriteVersion"
    ],
    "sys_id": "8a32434e-77e4-4c2b-a56d-0fc3555e970d",
    "sys_isFolderish": true,
    "sys_lastContributor": {
        "id": "00000000-0000-0000-0000-000000000001",
        "firstName": "Admin",
        "lastName": "User",
        "username": "admin",
        "email": "admin@example.com"
    },
    "sys_mixinTypes": [
        "SysFolderish"
    ],
    "sys_modified": "2026-09-01T12:08:35.540734146Z",
    "sys_name": "My Test Folder",
    "sys_parentId": "00000000-0000-0000-0000-000000000000",
    "sys_parentPath": "/",
    "sys_path": "/My Test Folder",
    "sys_pathDepth": 2,
    "sys_primaryType": "SysFolder",
    "sys_repository": "default",
    "sys_title": "My Test Folder"
}
```

#### Get the Created Document
After creating a document, you can retrieve it using its unique identifier (UUID). In this example, the UUID of the created document is `8a32434e-77e4-4c2b-a56d-0fc3555e970d`. You can use the following command to get the document details:
```shell
curl --request GET \
  --user admin:password \
  --url http://localhost:8080/api/documents/8a32434e-77e4-4c2b-a56d-0fc3555e970d
```

#### Search for Documents
You can search for documents in Hyland OpenArch using the search API. The following command searches for documents with the title "My Test Folder":
```shell
curl --request POST \
  --url http://localhost:8080/api/query \
  --user admin:password \
  --header 'Content-Type: application/json' \
  --data '{
  "repositoryId": "default",
  "query": "SELECT * FROM SysFolder WHERE sys_title = '\''My Test Folder'\''",
  "limit": 20,
  "offset": 0,
  "sort": [
    "sys_title"
  ]
}'
```
The result will be a JSON object containing the search results, including the documents that match the query.
```json
{
  "repositoryId": "default",
  "query": "SELECT * FROM SysFolder WHERE sys_title = 'My Test Folder' ORDER BY sys_title",
  "limit": 20,
  "offset": 0,
  "documents": [
    {
        "sys_ancestorIds": [
            "00000000-0000-0000-0000-000000000000"
        ],
        "sys_id": "8a32434e-77e4-4c2b-a56d-0fc3555e970d",
      ...
    }
  ],
  "totalCount": 1,
  "count": 1
}
```

#### Going Further
Check our [documentation](https://hyland.github.io/ContentIntelligence-Docs/) for further use cases and options.

## Integrating Hyland OpenArch
<!-- TODO: update link once the demo gets moved to the OSS org -->
Hyland OpenArch will run as a component in your AI platform stack.

To help you understand how you will integrate it with your existing solutions, we built a [fully functional Hyland OpenArch demo](https://github.com/aborroy/content-lake-app-deployment#content-lake-app-deployment) indexing content from Alfresco and Nuxeo repositories that you can freely take inspiration from.

## Giving Feedback and Getting Support
Discussions, questions and feedback are most welcome. Feel free to connect with us through the [discussions section](https://github.com/Hyland/OpenArch/discussions). Bug reports and feature requests should be reported through the [issues section](https://github.com/Hyland/OpenArch/issues).

## Contributing
<!-- TODO: confirm the branch in the blob path matches the repo default branch -->
Contributions are always welcome. Learn [how to contribute](https://github.com/Hyland/OpenArch/blob/main/CONTRIBUTING.md) here.

## Reporting a Security Issue
<!-- TODO: check link -->
Security reports can be safely transmitted to us by following these [security reporting guidelines](https://github.com/Hyland/OpenArch/security).

## License
Hyland OpenArch codebase is released under the Apache 2.0 license.
