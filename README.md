# Plumb5 – Mobile App Monitoring & Engagement Platform

An Android monitoring SDK (Java) with a Spring Boot + MySQL backend for tracking app events and engaging users.

## Features
- Android SDK: event tracking, push notifications (FCM), in-app messages/forms/banners, geofencing, beacon triggers, chat
- Spring Boot backend: REST APIs, license management, push sending, in-app message management, chat, admin-token auth
- Admin dashboard (static HTML/JS)

## Tech stack
Java, Android SDK, Spring Boot, Spring Data JPA/Hibernate, MySQL, Firebase Cloud Messaging, Maven, Gradle

## Structure
- `android-sdk/` – monitoring SDK source (`com.plumb5.monitor`)
- `backend/` – Spring Boot service (port 9090)
- `CHANGES.md` – details of features and fixes

## Run the backend
1. Install MySQL and update `backend/src/main/resources/application.properties` (DB user/password).
2. `cd backend && ./mvnw spring-boot:run`
