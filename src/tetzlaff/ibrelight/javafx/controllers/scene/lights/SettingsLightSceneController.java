package tetzlaff.ibrelight.javafx.controllers.scene.lights;//Created by alexk on 7/16/2017.

import java.net.URL;
import java.util.ResourceBundle;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import tetzlaff.ibrelight.javafx.util.SafeNumberStringConverter;
import tetzlaff.ibrelight.javafx.util.SafeNumberStringConverterPow10;
import tetzlaff.ibrelight.javafx.util.StaticUtilities;

public class SettingsLightSceneController implements Initializable
{
    @Override
    public void initialize(URL location, ResourceBundle resources)
    {
        setDisabled(true);

        StringConverter<Double> pow10converter = new StringConverter<Double>()
        {
            @Override
            public String toString(Double object)
            {
                String s = n10.toString(object);
                if (s.length() > 4)
                {
                    return s.substring(0, 4);
                }
                else
                {
                    return s;
                }
            }

            @Override
            public Double fromString(String string)
            {
                return null;
            }
        };

        distanceSlider.setLabelFormatter(pow10converter);

        intensitySlider.setLabelFormatter(pow10converter);
        StaticUtilities.bindLogScaleToLinear(intensitySlider.valueProperty(), trueIntensity);

        StaticUtilities.makeNumeric(xCenterTextField);
        StaticUtilities.makeNumeric(yCenterTextField);
        StaticUtilities.makeNumeric(zCenterTextField);

        StaticUtilities.makeClampedNumeric(0, Double.MAX_VALUE, distanceTextField);
        StaticUtilities.makeClampedNumeric(0, Double.MAX_VALUE, intensityTextField);

        StaticUtilities.makeWrapAroundNumeric(-180, 180, azimuthTextField);
        StaticUtilities.makeClampedNumeric(-90, 90, inclinationTextField);
    }

    @FXML private VBox root;
    @FXML private TextField xCenterTextField;
    @FXML private TextField yCenterTextField;
    @FXML private TextField zCenterTextField;
    @FXML private Slider xCenterSlider;
    @FXML private Slider yCenterSlider;
    @FXML private Slider zCenterSlider;
    @FXML private TextField azimuthTextField;
    @FXML private Slider azimuthSlider;
    @FXML private TextField inclinationTextField;
    @FXML private Slider inclinationSlider;
    @FXML private TextField distanceTextField;
    @FXML private Slider distanceSlider;
    @FXML private TextField intensityTextField;
    @FXML private Slider intensitySlider;
    @FXML private ColorPicker colorPicker;
    @FXML private ChoiceBox<LightType> lightTypeChoiceBox;

    private final DoubleProperty trueIntensity = new SimpleDoubleProperty(1);

    private final SafeNumberStringConverter n = new SafeNumberStringConverter(0);
    private final SafeNumberStringConverterPow10 n10 = new SafeNumberStringConverterPow10(1);

    public final ChangeListener<LightInstanceSetting> changeListener = (observable, oldValue, newValue) ->
    {
        if (oldValue != null)
        {
            unbind(oldValue);
        }

        if (newValue != null)
        {
            bind(newValue);
            setDisabled(newValue.isLocked() || newValue.isGroupLocked());
        }
        else
        {
            setDisabled(true);
        }
    };

    public void setDisabled(Boolean disabled)
    {
        root.setDisable(disabled);
    }

    private void bind(LightInstanceSetting c)
    {

        xCenterTextField.textProperty().bindBidirectional(c.targetXProperty(), n);
        yCenterTextField.textProperty().bindBidirectional(c.targetYProperty(), n);
        zCenterTextField.textProperty().bindBidirectional(c.targetZProperty(), n);
        azimuthTextField.textProperty().bindBidirectional(c.azimuthProperty(), n);
        inclinationTextField.textProperty().bindBidirectional(c.inclinationProperty(), n);
        distanceTextField.textProperty().bindBidirectional(c.log10DistanceProperty(), n10);
        intensityTextField.textProperty().bindBidirectional(c.intensityProperty(), n);
        xCenterSlider.valueProperty().bindBidirectional(c.targetXProperty());
        yCenterSlider.valueProperty().bindBidirectional(c.targetYProperty());
        zCenterSlider.valueProperty().bindBidirectional(c.targetZProperty());
        azimuthSlider.valueProperty().bindBidirectional(c.azimuthProperty());
        inclinationSlider.valueProperty().bindBidirectional(c.inclinationProperty());
        distanceSlider.valueProperty().bindBidirectional(c.log10DistanceProperty());

        trueIntensity.bindBidirectional(c.intensityProperty());

        lightTypeChoiceBox.valueProperty().bindBidirectional(c.lightTypeProperty());

        colorPicker.valueProperty().bindBidirectional(c.colorProperty());
    }

    private void unbind(LightInstanceSetting c)
    {

        xCenterTextField.textProperty().unbindBidirectional(c.targetXProperty());
        yCenterTextField.textProperty().unbindBidirectional(c.targetYProperty());
        zCenterTextField.textProperty().unbindBidirectional(c.targetZProperty());
        azimuthTextField.textProperty().unbindBidirectional(c.azimuthProperty());
        inclinationTextField.textProperty().unbindBidirectional(c.inclinationProperty());
        distanceTextField.textProperty().unbindBidirectional(c.log10DistanceProperty());
        intensityTextField.textProperty().unbindBidirectional(c.intensityProperty());
        xCenterSlider.valueProperty().unbindBidirectional(c.targetXProperty());
        yCenterSlider.valueProperty().unbindBidirectional(c.targetYProperty());
        zCenterSlider.valueProperty().unbindBidirectional(c.targetZProperty());
        azimuthSlider.valueProperty().unbindBidirectional(c.azimuthProperty());
        inclinationSlider.valueProperty().unbindBidirectional(c.inclinationProperty());
        distanceSlider.valueProperty().unbindBidirectional(c.log10DistanceProperty());

        trueIntensity.unbindBidirectional(c.intensityProperty());

        lightTypeChoiceBox.valueProperty().unbindBidirectional(c.lightTypeProperty());
        colorPicker.valueProperty().unbindBidirectional(c.colorProperty());
    }
}
