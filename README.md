Chronic Wound Detection and Segmentation App. Measurement of the area and perimiter of chronic wounds
Overview
This Android application is designed to assist healthcare professionals in detecting and segmenting chronic wounds on patients. For that, two algorithms was created. The detection model was direcly implemented into the app whereas the segmentation app was put on a server thanks to a flask app.
A calibration panel with known length and area (resp 3.5cm and 1.35) must be put next to the wound. It allows to measure the area and the perimeter of the chronic thanks to a cv2 function which is able to detect the circle inside the panel. Finally the result of the measurement is print to the user. 

Features
Image Capture: Utilize the smartphone camera to capture high-resolution images of chronic wounds directly within the app or load an image from your galery.

Detection: Employ cutting-edge machine learning algorithms to automatically detect the presence of chronic wounds and the calibration panel in the captured images.

Crop images: Crop the calibration panel and the chronic wounds thanks to the result of the detection part.

Segmentation: Apply image segmentation techniques to precisely delineate the boundaries of detected wounds. Take the crop chronic wound as an input. 

Detection of the calibration panel circle: Call the cv2HoughCircles function to detect the circle inside the crop calibration panel and return the radius in pixel of the circle. Make the conversion realcm/pixel.

Measurement: Calculate the area and the perimeter of the chronic wound thanks to segmentation and the calculation cm/pixel.

Print the result: Print the result to the user.

Installation
Clone or download the repository to your local machine.
Open the project in Android Studio.
Connect an Android device or start an emulator.
Build and run the application on the device/emulator.
Usage
Launch the app on your Android device.
Capture an image of the chronic wound using the built-in camera functionality or load an image from your gallery.
Wait for the app to process the image and detect.
Review the results, check if the chronic wound(s) and the calibration is delimited.
Push the button 'Calculate the area and the perimiter'. On back-end, segment the crop chronic wound and make the conversion cm/pixel
Measure the area and the perimeter of the chronic wound.
Print the result

Contribution
Contributions to the project are welcome! If you have suggestions for improvements, new features, or bug fixes, please feel free to open an issue or submit a pull request.
