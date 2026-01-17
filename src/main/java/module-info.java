module com.example.animemanager {
    requires javafx.controls;
    requires javafx.fxml;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires spring.data.jpa;
    requires spring.orm;
    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires org.slf4j;
    requires java.sql;
    requires com.zaxxer.hikari;

    opens com.example.animemanager.Entity to
            org.hibernate.orm.core,
            spring.core,
            spring.beans,
            spring.context,
            com.fasterxml.jackson.databind;

    opens com.example.animemanager.Repository to
            spring.core,
            spring.beans,
            spring.context,
            spring.data.jpa;

    opens com.example.animemanager to
            spring.core,
            spring.beans,
            spring.context,
            spring.boot;

    opens com.example.animemanager.Service to
            spring.core,
            spring.beans,
            spring.context;
    opens com.example.animemanager.UI.Controller to
            spring.core,
            spring.beans,
            spring.context,
            javafx.fxml;

    exports com.example.animemanager;
    exports com.example.animemanager.Entity;
    exports com.example.animemanager.Repository;
    exports com.example.animemanager.Service;
    exports com.example.animemanager.UI.Controller;
}