package managers;

import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import daos.UserDAO;
import exceptions.InvalidAttributeException;
import exceptions.UserNotFoundException;
import helpers.Metrics;
import java.util.Map;
import javax.inject.Inject;
import models.ReceivedWorkoutMeta;
import models.User;

public class SetAllReceivedWorkoutsSeenManager {

    private final UserDAO userDAO;
    private final Metrics metrics;

    @Inject
    public SetAllReceivedWorkoutsSeenManager(final UserDAO userDAO, final Metrics metrics) {
        this.userDAO = userDAO;
        this.metrics = metrics;
    }

    /**
     * Loops through all the received workouts of a user and sets them to seen.
     *
     * @param activeUser user whose requests are being set to seen.
     * @throws InvalidAttributeException if error with user item.
     * @throws UserNotFoundException     if active user is not found.
     */
    public void setAllReceivedWorkoutsSeen(final String activeUser)
        throws InvalidAttributeException, UserNotFoundException {
        final String classMethod = this.getClass().getSimpleName() + ".setAllReceivedWorkoutsSeen";
        this.metrics.commonSetup(classMethod);

        try {
            // todo have one that only does it by batch?
            final User user = this.userDAO.getUser(activeUser);
            final Map<String, ReceivedWorkoutMeta> receivedWorkouts = user.getReceivedWorkouts();
            for (String workoutId : receivedWorkouts.keySet()) {
                receivedWorkouts.get(workoutId).setSeen(true);
            }

            final UpdateItemSpec updateActiveUserData = new UpdateItemSpec()
                .withUpdateExpression("set "
                    + User.UNSEEN_RECEIVED_WORKOUTS + "=:seenVal, "
                    + User.RECEIVED_WORKOUTS + "=:receivedWorkoutsVal")
                .withValueMap(new ValueMap()
                    .withNumber(":seenVal", 0)
                    .withMap(":receivedWorkoutsVal", user.getReceivedWorkoutMetaMap()));
            this.userDAO.updateUser(activeUser, updateActiveUserData);

            this.metrics.commonClose(false);
        } catch (Exception e) {
            this.metrics.commonClose(false);
            throw e;
        }
    }
}