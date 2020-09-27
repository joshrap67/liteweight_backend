package managers;

import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import daos.UserDAO;
import exceptions.ManagerExecutionException;
import helpers.Metrics;
import helpers.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import models.ExerciseUser;
import models.User;
import responses.ExerciseUserResponse;

public class NewExerciseManager {

    private final UserDAO userDAO;
    private final Metrics metrics;

    @Inject
    public NewExerciseManager(final UserDAO userDAO, final Metrics metrics) {
        this.userDAO = userDAO;
        this.metrics = metrics;
    }

    /**
     * Creates a new exercise with the given input assuming that the input is valid and that the
     * user has not gone over the maximum number of custom exercises.
     *
     * @param activeUser   user that is creating this exercise.
     * @param exerciseName name of the new exercise.
     * @param focuses      list of the focuses that this exercise is apart of.
     * @return ExerciseUserResponse the newly created exercise
     * @throws Exception thrown if any input error
     */
    public ExerciseUserResponse newExercise(final String activeUser, final String exerciseName,
        final List<String> focuses) throws Exception {
        final String classMethod = this.getClass().getSimpleName() + ".newExercise";
        this.metrics.commonSetup(classMethod);

        try {
            final User user = this.userDAO.getUser(activeUser);

            List<String> focusList = new ArrayList<>(focuses);
            final String errorMessage = Validator.validNewExercise(user, exerciseName, focusList);

            if (!errorMessage.isEmpty()) {
                this.metrics.commonClose(false);
                throw new ManagerExecutionException(errorMessage);
            }

            // all input is valid so go ahead and make the new exercise
            ExerciseUser exerciseUser = new ExerciseUser(exerciseName,
                ExerciseUser.defaultVideoValue, focusList, false);
            String exerciseId = UUID.randomUUID().toString();

            final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withUpdateExpression("set " + User.EXERCISES + ".#exerciseId= :exerciseMap")
                .withNameMap(new NameMap().with("#exerciseId", exerciseId))
                .withValueMap(new ValueMap().withMap(":exerciseMap", exerciseUser.asMap()));
            this.userDAO.updateUser(user.getUsername(), updateItemSpec);

            this.metrics.commonClose(true);
            return new ExerciseUserResponse(exerciseId, exerciseUser);
        } catch (Exception e) {
            this.metrics.commonClose(false);
            throw e;
        }
    }

}
