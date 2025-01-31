/*
 *  Copyright (c) Michael Tetzlaff 2022
 *
 *  Licensed under GPLv3
 *  ( http://www.gnu.org/licenses/gpl-3.0.html )
 *
 *  This code is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package tetzlaff.ibrelight.export.resample;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.Window;
import tetzlaff.gl.core.Context;
import tetzlaff.ibrelight.core.IBRRequestQueue;
import tetzlaff.ibrelight.core.IBRRequestUI;
import tetzlaff.ibrelight.core.IBRelightModels;

public class ResampleRequestUI implements IBRRequestUI
{
    @FXML private TextField widthTextField;
    @FXML private TextField heightTextField;
    @FXML private TextField exportDirectoryField;
    @FXML private TextField targetVSetFileField;
    @FXML private Button runButton;

    private final FileChooser fileChooser = new FileChooser();
    private final DirectoryChooser directoryChooser = new DirectoryChooser();

    private Stage stage;

    private File lastDirectory;

    public static ResampleRequestUI create(Window window, IBRelightModels modelAccess) throws IOException
    {
        String fxmlFileName = "fxml/export/ResampleRequestUI.fxml";
        URL url = ResampleRequestUI.class.getClassLoader().getResource(fxmlFileName);
        assert url != null : "Can't find " + fxmlFileName;

        FXMLLoader fxmlLoader = new FXMLLoader(url);
        Parent parent = fxmlLoader.load();
        ResampleRequestUI resampleRequestUI = fxmlLoader.getController();

        resampleRequestUI.stage = new Stage();
        resampleRequestUI.stage.getIcons().add(new Image(new File("ibr-icon.png").toURI().toURL().toString()));
        resampleRequestUI.stage.setTitle("Resample request");
        resampleRequestUI.stage.setScene(new Scene(parent));
        resampleRequestUI.stage.initOwner(window);

        return resampleRequestUI;
    }

    @FXML
    private void exportDirectoryButtonAction()
    {
        this.directoryChooser.setTitle("Choose an export directory");
        if (exportDirectoryField.getText().isEmpty())
        {
            if (lastDirectory != null)
            {
                this.directoryChooser.setInitialDirectory(lastDirectory);
            }
        }
        else
        {
            File currentValue = new File(exportDirectoryField.getText());
            this.directoryChooser.setInitialDirectory(currentValue);
        }
        File file = this.directoryChooser.showDialog(stage.getOwner());
        if (file != null)
        {
            exportDirectoryField.setText(file.toString());
            lastDirectory = file;
        }
    }

    @FXML
    private void targetVSetFileButtonAction()
    {
        this.fileChooser.setTitle("Choose a target view set file");
        this.fileChooser.getExtensionFilters().clear();
        this.fileChooser.getExtensionFilters().add(new ExtensionFilter("View set files", "*.vset"));
        if (targetVSetFileField.getText().isEmpty())
        {
            if (lastDirectory != null)
            {
                this.fileChooser.setInitialDirectory(lastDirectory);
            }
        }
        else
        {
            File currentValue = new File(targetVSetFileField.getText());
            this.fileChooser.setInitialDirectory(currentValue.getParentFile());
            this.fileChooser.setInitialFileName(currentValue.getName());
        }
        File file = this.fileChooser.showOpenDialog(stage.getOwner());
        if (file != null)
        {
            targetVSetFileField.setText(file.toString());
            lastDirectory = file.getParentFile();
        }
    }

    @FXML
    public void cancelButtonAction(ActionEvent actionEvent)
    {
        stage.close();
    }

    @Override
    public <ContextType extends Context<ContextType>> void prompt(IBRRequestQueue<ContextType> requestQueue)
    {
        stage.show();

        runButton.setOnAction(event ->
        {
            //stage.close();

            requestQueue.addIBRRequest(
                new ResampleRequest<>(
                    Integer.parseInt(widthTextField.getText()),
                    Integer.parseInt(heightTextField.getText()),
                    new File(targetVSetFileField.getText()),
                    new File(exportDirectoryField.getText())));
        });
    }
}
