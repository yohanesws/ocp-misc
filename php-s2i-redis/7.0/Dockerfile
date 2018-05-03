# FROM registry.access.redhat.com/rhscl/php-70-rhel7:latest
FROM openshift/php:7.0

LABEL maintainer="Yohanes <ysono@redhat.com>" \
      description="${DESCRIPTION} with Redis include" \
      io.k8s.description="${DESCRIPTION} with Redis include"

USER root

RUN echo '[rhel-server-rhscl-7-rpms]' >>  /etc/yum.repos.d/rhscl.repo && \
    echo 'name=rhel-server-rhscl-7-rpms' >>  /etc/yum.repos.d/rhscl.repo && \
    echo 'baseurl=http://ocp-repo-1.osdec.gov.my/repos/rhel-server-rhscl-7-rpms' >>  /etc/yum.repos.d/rhscl.repo && \
    echo 'enabled=1' >>  /etc/yum.repos.d/rhscl.repo && \
    echo 'gpgcheck=0' >>  /etc/yum.repos.d/rhscl.repo && \
    yum install -y rh-php70-php-pear rh-php70-php-devel && \
    yum clean all -y && \
    pecl install redis

USER 1001