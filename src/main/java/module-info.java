module com.example.animemanager {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires com.fasterxml.jackson.databind;
    requires jakarta.persistence;
    requires org.hibernate.orm.core;

    opens com.example.animemanager to javafx.fxml;
    exports com.example.animemanager;
    opens com.example.animemanager.UI.Controller to javafx.fxml;
    exports com.example.animemanager.UI.Controller;
}