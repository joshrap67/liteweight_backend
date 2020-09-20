package managers;

import aws.DatabaseAccess;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.Put;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItem;
import exceptions.ManagerExecutionException;
import helpers.AttributeValueHelper;
import helpers.Metrics;
import helpers.UpdateItemData;
import helpers.Validator;
import helpers.WorkoutHelper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import models.Routine;
import models.User;
import models.Workout;
import models.WorkoutUser;
import responses.UserWithWorkout;

public class NewWorkoutManager {

    private final DatabaseAccess databaseAccess;
    private final Metrics metrics;

    @Inject
    public NewWorkoutManager(final DatabaseAccess databaseAccess, final Metrics metrics) {
        this.databaseAccess = databaseAccess;
        this.metrics = metrics;
    }

    /**
     * @param workoutName TODO
     * @return Result status that will be sent to frontend with appropriate data or error messages.
     */
    public UserWithWorkout execute(final String activeUser, final String workoutName,
        final Routine routine) throws Exception {
        final String classMethod = this.getClass().getSimpleName() + ".execute";
        this.metrics.commonSetup(classMethod);

        try {
            final User user = this.databaseAccess.getUser(activeUser);

            final String workoutId = UUID.randomUUID().toString();
            final String creationTime = Instant.now().toString();
            final String errorMessage = Validator.validNewWorkoutInput(workoutName, user, routine);

            if (!errorMessage.isEmpty()) {
                this.metrics.commonClose(false);
                throw new ManagerExecutionException(errorMessage);
            }
            // no error, so go ahead and try and insert this new workout along with updating active user
            final Workout newWorkout = new Workout();
            newWorkout.setCreationDate(creationTime);
            newWorkout.setCreator(activeUser);
            newWorkout.setMostFrequentFocus(WorkoutHelper.findMostFrequentFocus(user, routine));
            newWorkout.setWorkoutId(workoutId);
            newWorkout.setWorkoutName(workoutName.trim());
            newWorkout.setRoutine(routine);
            newWorkout.setCurrentDay(0);
            newWorkout.setCurrentWeek(0);

            final WorkoutUser workoutUser = new WorkoutUser();
            workoutUser.setWorkoutName(workoutName.trim());
            workoutUser.setAverageExercisesCompleted(0.0);
            workoutUser.setDateLast(creationTime);
            workoutUser.setTimesCompleted(0);
            workoutUser.setTotalExercisesSum(0);
            // need to set it here so frontend gets updated user item back
            user.setUserWorkouts(workoutId, workoutUser);

            // update all the exercises that are now apart of this workout
            WorkoutHelper.updateUserExercises(user, routine, workoutId, workoutName);

            final UpdateItemData updateItemData = new UpdateItemData(activeUser,
                DatabaseAccess.USERS_TABLE_NAME)
                .withUpdateExpression("set " +
                    User.CURRENT_WORKOUT + " = :currentWorkoutVal, " +
                    User.WORKOUTS + ".#workoutId= :workoutUserMap, " +
                    User.EXERCISES + "= :exercisesMap")
                .withValueMap(new ValueMap()
                    .withString(":currentWorkoutVal", workoutId)
                    .withMap(":workoutUserMap", workoutUser.asMap())
                    .withMap(":exercisesMap", user.getUserExercisesMap()))
                .withNameMap(new NameMap().with("#workoutId", workoutId));

            // want a transaction since more than one object is being updated at once
            final List<TransactWriteItem> actions = new ArrayList<>();
            actions.add(new TransactWriteItem().withUpdate(updateItemData.asUpdate()));
            actions.add(new TransactWriteItem()
                .withPut(new Put().withTableName(DatabaseAccess.WORKOUT_TABLE_NAME).withItem(
                    AttributeValueHelper.convertMapToAttributeValueMap(newWorkout.asMap()))));
            this.databaseAccess.executeWriteTransaction(actions);

            this.metrics.commonClose(true);
            return new UserWithWorkout(user, newWorkout);
        } catch (Exception e) {
            this.metrics.commonClose(false);
            throw e;
        }
    }
}
