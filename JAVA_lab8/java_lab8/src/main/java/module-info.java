module org.example.java_lab8 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;


    opens org.example.java_lab8 to javafx.fxml;
    exports org.example.java_lab8;
}