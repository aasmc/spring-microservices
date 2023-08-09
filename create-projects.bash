#!/usr/bin/env bash

mkdir microservices
cd microservices

spring init \
--boot-version=2.7.14 \
--type=gradle-project \
--build=gradle \
--java-version=17 \
--packaging=jar \
--name=product-service \
--package-name=ru.aasmc.microservices.core.product \
--groupId=ru.aasmc.microservices.core.product \
--dependencies=actuator,webflux \
--version=1.0.0-SNAPSHOT \
product-service

spring init \
--boot-version=2.7.14 \
--type=gradle-project \
--build=gradle \
--java-version=17 \
--packaging=jar \
--name=review-service \
--package-name=ru.aasmc.microservices.core.review \
--groupId=ru.aasmc.microservices.core.review \
--dependencies=actuator,webflux \
--version=1.0.0-SNAPSHOT \
review-service

spring init \
--boot-version=2.7.14 \
--type=gradle-project \
--build=gradle \
--java-version=17 \
--packaging=jar \
--name=recommendation-service \
--package-name=ru.aasmc.microservices.core.recommendation \
--groupId=ru.aasmc.microservices.core.recommendation \
--dependencies=actuator,webflux \
--version=1.0.0-SNAPSHOT \
recommendation-service

spring init \
--boot-version=2.7.14 \
--type=gradle-project \
--build=gradle \
--java-version=17 \
--packaging=jar \
--name=product-composite-service \
--package-name=ru.aasmc.microservices.composite.product \
--groupId=ru.aasmc.microservices.composite.product \
--dependencies=actuator,webflux \
--version=1.0.0-SNAPSHOT \
product-composite-service

cd ..