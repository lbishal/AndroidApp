“I certify that all solutions are entirely in my words and I have credited all sources in this submission.”

1. 

   Status of Implementation:

	I have implemented a basic version of both shake detection and step detection.
	I have also implemented a gesture detection application that can identify mathematical figures namely: cicle, square, and triangle.
	
		Utility of mathematical shape gesture detection:
			* It can be used for child educational games, e.g. the child can match the shape of shown figure with corresponding phone movement
			* It can be used as an user input mechanism to map into different phone/application functionalities.
	

   Challenges:

	I had an old android phone which I had to root to install custom ROM and support Android 9. I ended up getting many un-foreseen problems in this process. Had I known that I would have had to face these many issues with installing custom ROM, I would have rather borrowed a phone from the lab. Nonetheless, at the end, I succeeded in getting a LineageOS based custom ROM up and running for my investigations. 

	Further, I see quite some issues with signal noise. I did not use frequency based filtering because the sample might not be evenly spaced (I can resample and analyze but these all would cause further latency in decision). Having to implement the application in real-time kind of settings with low latency was challenging, especially when it came to the need of good noise filtering. I ended up using moving average filters in order to suppress some noise. Shake detector is then implemented based on changes in jerk value (derivative of acceleration). Step detector and gesture detector are based on template matching. I currently used fixed template matching based techniques which might not be optimal but has advantages in terms of ease of implementation compared to dynamic template matching (e.g. based on time-warping distances).


2. Link for the code
	https://github.com/lbishal/AndroidApp

	
3. 



		