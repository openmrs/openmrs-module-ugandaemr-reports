package org.openmrs.module.ugandaemrreports.definition.cohort.evaluator;

import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.annotation.Handler;
import org.openmrs.module.reporting.cohort.EvaluatedCohort;
import org.openmrs.module.reporting.cohort.definition.CohortDefinition;
import org.openmrs.module.reporting.cohort.definition.evaluator.CohortDefinitionEvaluator;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.EvaluationException;
import org.openmrs.module.reporting.evaluation.querybuilder.HqlQueryBuilder;
import org.openmrs.module.reporting.evaluation.service.EvaluationService;
import org.openmrs.module.ugandaemrreports.definition.cohort.definition.LostPatientsCohortDefinition;
import org.openmrs.module.ugandaemrreports.metadata.HIVMetadata;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.Map;

/**
 * Created by carapai on 28/06/2016.
 */
@Handler(supports = {LostPatientsCohortDefinition.class})
public class LostPatientsCohortDefinitionEvaluator implements CohortDefinitionEvaluator {

    @Autowired
    EvaluationService evaluationService;

    @Autowired
    HIVMetadata hivMetadata;

    @Override
    public EvaluatedCohort evaluate(CohortDefinition cohortDefinition, EvaluationContext context) throws EvaluationException {
        EvaluatedCohort ret = new EvaluatedCohort(cohortDefinition, context);
        LostPatientsCohortDefinition cd = (LostPatientsCohortDefinition) cohortDefinition;

        HqlQueryBuilder obsQuery = new HqlQueryBuilder();
        obsQuery.select("o.person.personId", "max(o.valueDatetime)");
        obsQuery.from(Obs.class, "o");
        //obsQuery.wherePersonIn("o.person.personId", context);
        obsQuery.whereEqual("o.concept", hivMetadata.getReturnVisitDate());

        if (cd.getStartDate() != null && cd.getEndDate() != null) {
            obsQuery.whereBetweenInclusive("o.valueDatetime", cd.getStartDate(), cd.getEndDate());
        } else if (cd.getStartDate() != null) {
            obsQuery.whereGreaterOrEqualTo("o.valueDatetime", cd.getStartDate());
        } else if (cd.getEndDate() != null) {
            obsQuery.whereLessOrEqualTo("o.valueDatetime", cd.getEndDate());
        }
        obsQuery.whereEqual("o.person.personVoided", false);

        obsQuery.groupBy("o.person.personId");

        HqlQueryBuilder encounterQuery = new HqlQueryBuilder();
        encounterQuery.select("e.patient.patientId", "min(e.encounterDatetime)");
        encounterQuery.from(Encounter.class, "e");
        //encounterQuery.wherePersonIn("e.patient.patientId", context);

        if (cd.getStartDate() != null) {
            encounterQuery.whereGreaterOrEqualTo("e.encounterDatetime", cd.getStartDate());
        }

        encounterQuery.whereEqual("e.patient.personVoided", false);

        encounterQuery.groupBy("e.patient.patientId");

        Map<Integer, Date> obsResults = evaluationService.evaluateToMap(obsQuery, Integer.class, Date.class, context);
        Map<Integer, Date> encounterResults = evaluationService.evaluateToMap(encounterQuery, Integer.class, Date.class, context);

        for (Map.Entry<Integer, Date> entry : obsResults.entrySet()) {

            Date localDate = encounterResults.get(entry.getKey());

            if (localDate != null) {

                Integer daysBetweenLastAppointmentAndCurrentDate = DateUtil.getDaysBetween(entry.getValue(),localDate);

                if (cd.getMaximumDays() != null && cd.getMinimumDays() != null && daysBetweenLastAppointmentAndCurrentDate >= cd.getMinimumDays() && daysBetweenLastAppointmentAndCurrentDate <= cd.getMaximumDays()) {
                    ret.addMember(entry.getKey());
                } else if (cd.getMinimumDays() != null && cd.getMaximumDays() == null && daysBetweenLastAppointmentAndCurrentDate >= cd.getMinimumDays()) {
                    ret.addMember(entry.getKey());
                } else if (cd.getMaximumDays() != null && cd.getMinimumDays() == null && daysBetweenLastAppointmentAndCurrentDate <= cd.getMaximumDays()) {
                    ret.addMember(entry.getKey());
                }
            }
        }
        return ret;
    }
}
