FROM registry.access.redhat.com/openshift3/jenkins-slave-base-rhel7:v3.10

LABEL maintainer="Yohanes <ysono@redhat.com>"\
      com.redhat.component="jenkins-slave-mysql-client" \
      name="jenkins-slave-mysql-client" \
      architecture="x86_64" \
      io.k8s.display-name="Jenkins Slave Image Management" \
      io.k8s.description="MYSQL Client on top of the jenkins slave base image" \
      io.openshift.tags="openshift,jenkins,slave,mysql"
USER root

RUN yum repolist > /dev/null && \
    yum clean all && \
    INSTALL_PKGS="mysql" && \
    yum install -y --enablerepo=rhel-7-server-extras-rpms --setopt=tsflags=nodocs $INSTALL_PKGS && \
    rpm -V $INSTALL_PKGS && \
    yum clean all

USER 1001