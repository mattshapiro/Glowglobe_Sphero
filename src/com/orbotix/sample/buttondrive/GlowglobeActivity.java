package com.orbotix.sample.buttondrive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import orbotix.macro.Calibrate;
import orbotix.macro.MacroObject;
import orbotix.macro.Roll;
import orbotix.robot.app.StartupActivity;
import orbotix.robot.base.CollisionDetectedAsyncData;
import orbotix.robot.base.ConfigureCollisionDetectionCommand;
import orbotix.robot.base.DeviceAsyncData;
import orbotix.robot.base.DeviceMessenger;
import orbotix.robot.base.RGBLEDOutputCommand;
import orbotix.robot.base.Robot;
import orbotix.robot.base.RobotProvider;
import orbotix.robot.base.RollCommand;
import orbotix.robot.base.CollisionDetectedAsyncData.CollisionPower;
import orbotix.robot.base.DeviceMessenger.AsyncDataListener;
import orbotix.robot.sensor.Acceleration;

/**
 * Activity for controlling the Sphero with five control buttons.
 */
public class GlowglobeActivity extends Activity
{
    /**
     * ID for starting the StartupActivity for result
     */
    private final static int STARTUP_ACTIVITY = 0;
    
    /**
     * States the ball can be in
     */
    private enum MotionState {
    	WAIT,
    	MOVE
    };
    
    private enum LightState {
    	ON,
    	OFF
    };
    
    
    private boolean isStartButtonClicked = false;
    private MotionState motionState;
    private LightState lightState;
    										//  ms    s   m
    private static final long SLEEP_TIME_MS = 1000 * 60 * 2;
	private static final int COLLISION_THRESHHOLD = 50;

    /**
     * Robot to control
     */
    private Robot mRobot;
    
    private MacroObject macro;
    
    /**
     * Last known heading
     */
    private float fHeading;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        lightState = LightState.OFF;
        motionState = MotionState.WAIT;
        macro = new MacroObject();
    }

    /**
     * Connect to the robot when the Activity starts
     */
    @Override
    protected void onStart() {
        super.onStart();
        
        if(mRobot == null){

            //Connect to the Robot
            Intent i = new Intent(this, StartupActivity.class);
            startActivityForResult(i, STARTUP_ACTIVITY);
        }
    }

    /**
     * Get the robot id from the StartupActivity result, and use the RobotProvider singleton to 
     * get an instance of the connected robot
     * @param requestCode The request code from the returned Activity
     * @param resultCode The result code from the returned Activity
     * @param data The Intent containing the result data tuple. The robot id is under the key 
     *             in StartupActivity.EXTRA_ROBOT_ID
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if(requestCode == STARTUP_ACTIVITY && resultCode == RESULT_OK){
            
            final String robot_id = data.getStringExtra(StartupActivity.EXTRA_ROBOT_ID);
            
            if(robot_id != null && !robot_id.equals("")){
                mRobot = RobotProvider.getDefaultProvider().findRobot(robot_id);
            }
            
            // Start streaming collision detection data
 			//// First register a listener to process the data
 			DeviceMessenger.getInstance().addAsyncDataListener(mRobot,
 					mCollisionListener);

 			//// Now send a command to enable streaming collisions
 			//// 
 			ConfigureCollisionDetectionCommand.sendCommand(mRobot, ConfigureCollisionDetectionCommand.DEFAULT_DETECTION_METHOD,
 					5, 5, 100, 100, 100);
        }
    }

    /**
     * When the user clicks "STOP", stop the Robot.
     * @param v The View that had been clicked
     */
    public void onButtonClick(View v){
    	isStartButtonClicked = !isStartButtonClicked;
    	switchMotionState(MotionState.WAIT);
    	if(isStartButtonClicked) {
    			switchLightState(LightState.ON);
    	}else{
    			switchLightState(LightState.OFF);
    			stopSphero();
    	}
    }
    
    private void switchLightState(LightState switchTo) {
    	int val = (switchTo.equals(LightState.ON)) ? 0xff : 0;  
    	RGBLEDOutputCommand.sendCommand(mRobot, val, val, val);
		lightState = switchTo;
	}
    
    private void switchMotionState(MotionState switchTo) {
    	switchMotionState(switchTo, null);
    }
    
    private void switchMotionState(MotionState switchTo, Acceleration acceleration) {
    	motionState = switchTo;
    }
    
    private final AsyncDataListener mCollisionListener = new AsyncDataListener() {

		public void onDataReceived(DeviceAsyncData asyncData) {
			if (asyncData instanceof CollisionDetectedAsyncData) {
				final CollisionDetectedAsyncData collisionData = (CollisionDetectedAsyncData) asyncData;

				// Update the UI with the collision data
				if(isStartButtonClicked) {
					Acceleration acceleration = collisionData.getImpactAcceleration();
					CollisionPower power = collisionData.getImpactPower();
					switch(motionState) {
						case MOVE:
							System.out.println("POWER = " + (power.x + power.y) + " / THRESSHOLD = " + COLLISION_THRESHHOLD);
							if(Math.abs(power.x) + Math.abs(power.y) > COLLISION_THRESHHOLD) {
								stopSphero();
								return;
							}
							break;
							// fall through
						case WAIT: 
							int heading = (int)Math.atan2(acceleration.x * 100, acceleration.y * 100);
							//RollCommand.sendCommand(mRobot, fHeading, 0.3f);
							System.out.println("rolling @ "+ heading);
							macro.addCommand(new Calibrate(heading, 0));
							macro.addCommand(new Roll(0.3f, 0, 1000));
							macro.setRobot(mRobot);
							macro.playMacro();
							switchMotionState(MotionState.MOVE);
							break;
					}
				}
			}
		}
	};
	
	private void stopSphero() {
		macro.stopMacro();
		RollCommand.sendCommand(mRobot, 0, 0);
		switchMotionState(MotionState.WAIT);
	}

    /**
     * When the user clicks a control button, roll the Robot in that direction
     * @param v The View that had been clicked
     */
//    public void onControlClick(View v){
//        
//        //Find the heading, based on which button was clicked
//        final float heading;
//        switch (v.getId()){
//            
//            case R.id.ninety_button:
//                heading = 90f;
//                break;
//            
//            case R.id.one_eighty_button:
//                heading = 180f;
//                break;
//            
//            case R.id.two_seventy_button:
//                heading = 270f;
//                break;
//
//            default:
//                heading = 0f;
//                break;
//        }
//        
//        //Set speed. 60% of full speed
//        final float speed = 0.6f;
//        
//        //Roll robot
//        RollCommand.sendCommand(mRobot, heading, speed);
//    }

	/**
     * Disconnect from the robot when the Activity stops
     */
    @Override
    protected void onStop() {
        super.onStop();

        //disconnect robot
        RobotProvider.getDefaultProvider().disconnectControlledRobots();
        
        // Assume that collision detection is configured and disable it.
 		ConfigureCollisionDetectionCommand.sendCommand(mRobot, ConfigureCollisionDetectionCommand.DISABLE_DETECTION_METHOD, 0, 0, 0, 0, 0);
 		
 		// Remove async data listener
 		DeviceMessenger.getInstance().removeAsyncDataListener(mRobot, mCollisionListener);
 		
 		// Disconnect from the robot.
 		RobotProvider.getDefaultProvider().removeAllControls();
    }
}
