package managers;

import aws.DatabaseAccess;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItem;
import exceptions.ManagerExecutionException;
import helpers.Metrics;
import helpers.UpdateItemData;
import helpers.Validator;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import models.User;
import models.Workout;
import models.WorkoutUser;

public class RenameWorkoutManager {

    private final DatabaseAccess databaseAccess;
    private final Metrics metrics;

    @Inject
    public RenameWorkoutManager(final DatabaseAccess databaseAccess, final Metrics metrics) {
        this.databaseAccess = databaseAccess;
        this.metrics = metrics;
    }

    /**
     * @param workoutId TODO
     * @return Result status that will be sent to frontend with appropriate data or error messages.
     */
    public User execute(final String activeUser, final String workoutId,
        final String newWorkoutName) throws Exception {
        final String classMethod = this.getClass().getSimpleName() + ".execute";
        this.metrics.commonSetup(classMethod);

        try {
            final User user = this.databaseAccess.getUser(activeUser);

            final String errorMessage = Validator.validWorkoutName(newWorkoutName, user);
            if (!errorMessage.isEmpty()) {
                this.metrics.commonClose(false);
                throw new ManagerExecutionException(errorMessage);
            }

            // no error, so go ahead and try and rename the workout
            Workout workout = this.databaseAccess.getWorkout(workoutId);
            workout.setWorkoutName(newWorkoutName);
            // update all the exercises that are apart of this newly renamed workout
            updateUserExercises(user, workoutId, newWorkoutName);
            WorkoutUser workoutUser = user.getUserWorkouts().get(workoutId);
            workoutUser.setWorkoutName(newWorkoutName);

            final UpdateItemData updateUserItemData = new UpdateItemData(activeUser,
                DatabaseAccess.USERS_TABLE_NAME)
                .withUpdateExpression("set " +
                    User.WORKOUTS + ".#workoutId = :workoutsMap, " +
                    User.EXERCISES + "= :exercisesMap")
                .withValueMap(new ValueMap()
                    .withMap(":workoutsMap", workoutUser.asMap())
                    .withMap(":exercisesMap", user.getUserExercisesMap()))
                .withNameMap(new NameMap().with("#workoutId", workoutId));

            final UpdateItemData updateWorkoutItemData = new UpdateItemData(workoutId,
                DatabaseAccess.WORKOUT_TABLE_NAME)
                .withUpdateExpression("set " + Workout.WORKOUT_NAME + "= :workoutNameVal")
                .withValueMap(new ValueMap().withString(":workoutNameVal", newWorkoutName));
            // want a transaction since more than one object is being updated at once
            final List<TransactWriteItem> actions = new ArrayList<>();
            actions.add(new TransactWriteItem().withUpdate(updateUserItemData.asUpdate()));
            actions.add(new TransactWriteItem().withUpdate(updateWorkoutItemData.asUpdate()));
            this.databaseAccess.executeWriteTransaction(actions);

            this.metrics.commonClose(true);
            return user;
        } catch (Exception e) {
            this.metrics.commonClose(false);
            throw e;
        }
    }

    private static void updateUserExercises(final User user, final String workoutId,
        final String newWorkoutName) {
        // loops through all user exercises and updates the old workout name with the newly renamed one
        for (String exerciseId : user.getUserExercises().keySet()) {
            if (user.getUserExercises().get(exerciseId).getWorkouts().containsKey(workoutId)) {
                // old workout name found, replace it
                user.getUserExercises().get(exerciseId).getWorkouts()
                    .put(workoutId, newWorkoutName);
            }
        }
    }
}
