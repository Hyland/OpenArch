This module registers a component that defines a Transaction Manager as well
as a JCA-compatible Connection Manager in order for JCA resources to be
usable with connection pooling.

The current implementation is using Apache Geronimo's geronimo-transaction
and geronimo-connector. (This implementation require that apache-geronimo JARs are
deployed as third party libraries in the product)
