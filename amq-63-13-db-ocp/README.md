Using S2I 

To build :

s2i build . registry.access.redhat.com/jboss-amq-6/amq63-openshift:1.3 registry.access.redhat.com/jboss-amq-6/amq63-openshift-mariadb:1.3


To Run sample:

docker run -it --rm --net test-network -e JDBC_DRIVER=org.mariadb.jdbc.Driver -e JDBC_URL=jdbc:mariadb://mariadb:3306/amq -e JDBC_USERNAME=amq -e JDBC_PASSWORD=amq -e JDBC_MAX_POOL=10 -e JDBC_MIN_POOL=5 -e JDBC_MAX_WAIT=3000  registry.access.redhat.com/jboss-amq-6/amq63-openshift-mariadb:1.3