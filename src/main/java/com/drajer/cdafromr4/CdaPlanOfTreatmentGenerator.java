package com.drajer.cdafromr4;

import com.drajer.cda.utils.CdaGeneratorConstants;
import com.drajer.cda.utils.CdaGeneratorUtils;
import com.drajer.sof.model.LaunchDetails;
import com.drajer.sof.model.R4FhirData;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CdaPlanOfTreatmentGenerator {

  private static final Logger logger = LoggerFactory.getLogger(CdaPlanOfTreatmentGenerator.class);

  private CdaPlanOfTreatmentGenerator() {}

  public static String generatePlanOfTreatmentSection(R4FhirData data, LaunchDetails details) {

    StringBuilder sb = new StringBuilder(2000);

    List<ServiceRequest> sr = getValidServiceRequests(data);
    List<DiagnosticReport> reports = getValidDiagnosticOrders(data);

    if ((sr != null && !sr.isEmpty()) || (reports != null && !reports.isEmpty())) {
      logger.debug("Found a total of {} service request objects to translate to CDA.", sr.size());

      // Generate the component and section end tags
      sb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.COMP_EL_NAME));
      sb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.SECTION_EL_NAME));

      sb.append(
          CdaGeneratorUtils.getXmlForTemplateId(CdaGeneratorConstants.CAREPLAN_SEC_TEMPLATE_ID));
      sb.append(
          CdaGeneratorUtils.getXmlForTemplateId(
              CdaGeneratorConstants.CAREPLAN_SEC_TEMPLATE_ID,
              CdaGeneratorConstants.CAREPLAN_SEC_TEMPLATE_ID_EXT));

      sb.append(
          CdaGeneratorUtils.getXmlForCD(
              CdaGeneratorConstants.CODE_EL_NAME,
              CdaGeneratorConstants.CAREPLAN_SEC_CODE,
              CdaGeneratorConstants.LOINC_CODESYSTEM_OID,
              CdaGeneratorConstants.LOINC_CODESYSTEM_NAME,
              CdaGeneratorConstants.CAREPLAN_SEC_NAME));

      // Add Title
      sb.append(
          CdaGeneratorUtils.getXmlForText(
              CdaGeneratorConstants.TITLE_EL_NAME, CdaGeneratorConstants.CAREPLAN_SEC_TITLE));

      // Add Narrative Text
      sb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.TEXT_EL_NAME));

      // Create Table Header.
      List<String> list = new ArrayList<>();
      list.add(CdaGeneratorConstants.POT_OBS_TABLE_COL_1_TITLE);
      list.add(CdaGeneratorConstants.POT_OBS_TABLE_COL_2_TITLE);
      sb.append(
          CdaGeneratorUtils.getXmlForTableHeader(
              list, CdaGeneratorConstants.TABLE_BORDER, CdaGeneratorConstants.TABLE_WIDTH));

      // Add Table Body
      sb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.TABLE_BODY_EL_NAME));

      int rowNum = 1;
      StringBuilder potObsXml = new StringBuilder();
      StringBuilder drXml = new StringBuilder();
      for (ServiceRequest s : sr) {

        Pair<String, Boolean> srDisplayName =
            CdaFhirUtilities.getCodeableConceptDisplayForCodeSystem(
                s.getCode(), CdaGeneratorConstants.FHIR_LOINC_URL, false);

        if (srDisplayName.getValue0().isEmpty()) {
          srDisplayName.setAt0(CdaGeneratorConstants.UNKNOWN_VALUE);
        }

        String serviceDate = CdaFhirUtilities.getStringForType(s.getOccurrence());
        logger.debug("Service Date for display {} ", serviceDate);

        if (serviceDate.isEmpty() && s.getAuthoredOnElement() != null) {
          serviceDate = CdaFhirUtilities.getDisplayStringForDateTimeType(s.getAuthoredOnElement());
        }

        Map<String, String> bodyvals = new LinkedHashMap<>();
        bodyvals.put(
            CdaGeneratorConstants.POT_OBS_TABLE_COL_1_BODY_CONTENT, srDisplayName.getValue0());
        bodyvals.put(CdaGeneratorConstants.POT_OBS_TABLE_COL_2_BODY_CONTENT, serviceDate);

        sb.append(CdaGeneratorUtils.addTableRow(bodyvals, rowNum));

        String contentRef =
            CdaGeneratorConstants.POT_OBS_TABLE_COL_1_BODY_CONTENT + Integer.toString(rowNum);

        rowNum++;

        potObsXml.append(getPlannedObservationXml(s, details, contentRef));
      }

      for (DiagnosticReport dr : reports) {

        Pair<String, Boolean> drDisplayName =
            CdaFhirUtilities.getCodeableConceptDisplayForCodeSystem(
                dr.getCode(), CdaGeneratorConstants.FHIR_LOINC_URL, false);

        if (drDisplayName.getValue0().isEmpty()) {
          drDisplayName.setAt0(CdaGeneratorConstants.UNKNOWN_VALUE);
        }

        String orderDate = CdaFhirUtilities.getStringForType(dr.getEffective());
        logger.debug("Order Date for display {} ", orderDate);

        if (orderDate.isEmpty() && dr.getIssued() != null) {
          logger.info("Order Date is empty");
        }

        Map<String, String> bodyvals = new LinkedHashMap<>();
        bodyvals.put(
            CdaGeneratorConstants.POT_OBS_TABLE_COL_1_BODY_CONTENT, drDisplayName.getValue0());
        bodyvals.put(CdaGeneratorConstants.POT_OBS_TABLE_COL_2_BODY_CONTENT, orderDate);

        sb.append(CdaGeneratorUtils.addTableRow(bodyvals, rowNum));

        String contentRef =
            CdaGeneratorConstants.POT_OBS_TABLE_COL_1_BODY_CONTENT + Integer.toString(rowNum);

        rowNum++;

        drXml.append(getDiagnosticReportXml(dr, details, contentRef));
      }

      // Close the Text Element
      sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.TABLE_BODY_EL_NAME));
      sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.TABLE_EL_NAME));
      sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.TEXT_EL_NAME));

      // Add Entries
      sb.append(potObsXml);
      sb.append(drXml);

      // Complete the section end tags.
      sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.SECTION_EL_NAME));
      sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.COMP_EL_NAME));

    } else {

      sb.append(generateEmptyPlanOfTreatmentSection());
    }

    return sb.toString();
  }

  public static String getPlannedObservationXml(
      ServiceRequest sr, LaunchDetails details, String contentRef) {

    StringBuilder sb = new StringBuilder();

    // Generate the entry
    sb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.ENTRY_EL_NAME));
    sb.append(
        CdaGeneratorUtils.getXmlForAct(
            CdaGeneratorConstants.OBS_ACT_EL_NAME,
            CdaGeneratorConstants.OBS_CLASS_CODE,
            CdaGeneratorConstants.MOOD_CODE_RQO));

    sb.append(CdaGeneratorUtils.getXmlForTemplateId(CdaGeneratorConstants.PLANNED_OBS_TEMPLATE_ID));
    sb.append(
        CdaGeneratorUtils.getXmlForTemplateId(
            CdaGeneratorConstants.PLANNED_OBS_TEMPLATE_ID,
            CdaGeneratorConstants.PLANNED_OBS_TEMPLATE_ID_EXT));

    List<String> matchedTriggerCodes =
        CdaFhirUtilities.getMatchedCodesForResourceAndUrl(
            details, "ServiceRequest", CdaGeneratorConstants.FHIR_LOINC_URL);

    String codeXml = "";
    // Add Trigger code template if the code matched the Url in the Service Request.
    if (matchedTriggerCodes != null && !matchedTriggerCodes.isEmpty()) {
      logger.info("Found a Matched Code that is for Service Request");

      String mCd =
          CdaFhirUtilities.getMatchingCodeFromCodeableConceptForCodeSystem(
              matchedTriggerCodes, sr.getCode(), CdaGeneratorConstants.FHIR_LOINC_URL);

      if (!mCd.isEmpty()) {

        sb.append(
            CdaGeneratorUtils.getXmlForTemplateId(
                CdaGeneratorConstants.LAB_TEST_ORDER_TRIGGER_CODE_TEMPLATE,
                CdaGeneratorConstants.LAB_TEST_ORDER_TRIGGER_CODE_TEMPLATE_EXT));

        codeXml =
            CdaGeneratorUtils.getXmlForCDWithValueSetAndVersion(
                CdaGeneratorConstants.CODE_EL_NAME,
                mCd,
                CdaGeneratorConstants.LOINC_CODESYSTEM_OID,
                CdaGeneratorConstants.LOINC_CODESYSTEM_NAME,
                details.getRctcOid(),
                details.getRctcVersion(),
                "",
                contentRef);
      } else {
        logger.info(
            "Did not find the code value in the matched codes, make it a regular Planned Observation");
      }
    }

    // add Id
    sb.append(CdaGeneratorUtils.getXmlForIIUsingGuid());

    if (codeXml.isEmpty()) {
      List<CodeableConcept> ccs = new ArrayList<>();
      ccs.add(sr.getCode());
      codeXml =
          CdaFhirUtilities.getCodeableConceptXmlForCodeSystem(
              ccs,
              CdaGeneratorConstants.CODE_EL_NAME,
              false,
              CdaGeneratorConstants.FHIR_LOINC_URL,
              false);

      if (!codeXml.isEmpty()) {
        sb.append(codeXml);
      } else {
        sb.append(
            CdaFhirUtilities.getCodeableConceptXml(ccs, CdaGeneratorConstants.CODE_EL_NAME, false));
      }
    } else {
      sb.append(codeXml);
    }

    sb.append(
        CdaGeneratorUtils.getXmlForCD(
            CdaGeneratorConstants.STATUS_CODE_EL_NAME, CdaGeneratorConstants.ACTIVE_STATUS));

    Pair<Date, TimeZone> effDate = CdaFhirUtilities.getActualDate(sr.getOccurrence());
    if (effDate.getValue0() == null) {
      logger.debug("Use Authored Date");

      effDate.setAt0(sr.getAuthoredOn());
    }

    sb.append(
        CdaGeneratorUtils.getXmlForEffectiveTime(
            CdaGeneratorConstants.EFF_TIME_EL_NAME, effDate.getValue0(), effDate.getValue1()));

    // End Tag for Entry
    sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.OBS_ACT_EL_NAME));
    sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.ENTRY_EL_NAME));

    return sb.toString();
  }

  public static String getDiagnosticReportXml(
      DiagnosticReport dr, LaunchDetails details, String contentRef) {

    StringBuilder sb = new StringBuilder();

    // Generate the entry
    sb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.ENTRY_EL_NAME));
    sb.append(
        CdaGeneratorUtils.getXmlForAct(
            CdaGeneratorConstants.OBS_ACT_EL_NAME,
            CdaGeneratorConstants.OBS_CLASS_CODE,
            CdaGeneratorConstants.MOOD_CODE_RQO));

    sb.append(CdaGeneratorUtils.getXmlForTemplateId(CdaGeneratorConstants.PLANNED_OBS_TEMPLATE_ID));
    sb.append(
        CdaGeneratorUtils.getXmlForTemplateId(
            CdaGeneratorConstants.PLANNED_OBS_TEMPLATE_ID,
            CdaGeneratorConstants.PLANNED_OBS_TEMPLATE_ID_EXT));

    List<String> matchedTriggerCodes =
        CdaFhirUtilities.getMatchedCodesForResourceAndUrl(
            details, "DiagnosticReport", CdaGeneratorConstants.FHIR_LOINC_URL);

    String codeXml = "";
    // Add Trigger code template if the code matched the Url in the Service Request.
    if (matchedTriggerCodes != null && !matchedTriggerCodes.isEmpty()) {
      logger.info("Found a Matched Code that is for DiagnosticReport");

      String mCd =
          CdaFhirUtilities.getMatchingCodeFromCodeableConceptForCodeSystem(
              matchedTriggerCodes, dr.getCode(), CdaGeneratorConstants.FHIR_LOINC_URL);

      if (!mCd.isEmpty()) {

        sb.append(
            CdaGeneratorUtils.getXmlForTemplateId(
                CdaGeneratorConstants.LAB_TEST_ORDER_TRIGGER_CODE_TEMPLATE,
                CdaGeneratorConstants.LAB_TEST_ORDER_TRIGGER_CODE_TEMPLATE_EXT));

        codeXml =
            CdaGeneratorUtils.getXmlForCDWithValueSetAndVersion(
                CdaGeneratorConstants.CODE_EL_NAME,
                mCd,
                CdaGeneratorConstants.LOINC_CODESYSTEM_OID,
                CdaGeneratorConstants.LOINC_CODESYSTEM_NAME,
                details.getRctcOid(),
                details.getRctcVersion(),
                "",
                contentRef);
      } else {
        logger.debug(
            "Did not find the code value in the matched codes, make it a regular Planned Observation");
      }
    }

    // add Id
    sb.append(CdaGeneratorUtils.getXmlForIIUsingGuid());

    if (codeXml.isEmpty()) {
      List<CodeableConcept> ccs = new ArrayList<>();
      ccs.add(dr.getCode());
      codeXml =
          CdaFhirUtilities.getCodeableConceptXmlForCodeSystem(
              ccs,
              CdaGeneratorConstants.CODE_EL_NAME,
              false,
              CdaGeneratorConstants.FHIR_LOINC_URL,
              false);

      if (!codeXml.isEmpty()) {
        sb.append(codeXml);
      } else {
        sb.append(
            CdaFhirUtilities.getCodeableConceptXml(ccs, CdaGeneratorConstants.CODE_EL_NAME, false));
      }
    } else {
      sb.append(codeXml);
    }

    sb.append(
        CdaGeneratorUtils.getXmlForCD(
            CdaGeneratorConstants.STATUS_CODE_EL_NAME, CdaGeneratorConstants.ACTIVE_STATUS));

    Pair<Date, TimeZone> effDate = CdaFhirUtilities.getActualDate(dr.getEffective());
    if (effDate.getValue0() == null) {
      logger.debug("Use Authored Date");

      effDate.setAt0(dr.getIssued());
    }

    sb.append(
        CdaGeneratorUtils.getXmlForEffectiveTime(
            CdaGeneratorConstants.EFF_TIME_EL_NAME, effDate.getValue0(), effDate.getValue1()));

    // End Tag for Entry
    sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.OBS_ACT_EL_NAME));
    sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.ENTRY_EL_NAME));

    return sb.toString();
  }

  public static List<ServiceRequest> getValidServiceRequests(R4FhirData data) {

    List<ServiceRequest> sr = new ArrayList<>();

    if (data.getServiceRequests() != null && !data.getServiceRequests().isEmpty()) {

      logger.debug(
          "Total num of Service Requests available for Patient {}",
          data.getServiceRequests().size());

      for (ServiceRequest s : data.getServiceRequests()) {

        if (s.getCode() != null
            && s.getCode().getCoding() != null
            && !s.getCode().getCoding().isEmpty()
            && Boolean.TRUE.equals(
                CdaFhirUtilities.isCodingPresentForCodeSystem(
                    s.getCode().getCoding(), CdaGeneratorConstants.FHIR_LOINC_URL))) {

          logger.debug("Found a Service Request with a LOINC code");
          sr.add(s);
        } else {
          logger.info(
              " Ignoring Service Request with id {} as it is not having a loinc code ",
              s.getIdElement().getIdPart());
        }
      }
    } else {
      logger.info("No Valid Service Requests in the bundle to process");
    }

    return sr;
  }

  public static String generateEmptyPlanOfTreatmentSection() {

    StringBuilder sb = new StringBuilder();

    // Generate the component and section end tags
    sb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.COMP_EL_NAME));
    sb.append(
        CdaGeneratorUtils.getXmlForNFSection(
            CdaGeneratorConstants.SECTION_EL_NAME, CdaGeneratorConstants.NF_NI));

    // Add Plan of Treatment Template Id
    sb.append(
        CdaGeneratorUtils.getXmlForTemplateId(CdaGeneratorConstants.CAREPLAN_SEC_TEMPLATE_ID));
    sb.append(
        CdaGeneratorUtils.getXmlForTemplateId(
            CdaGeneratorConstants.CAREPLAN_SEC_TEMPLATE_ID,
            CdaGeneratorConstants.CAREPLAN_SEC_TEMPLATE_ID_EXT));

    sb.append(
        CdaGeneratorUtils.getXmlForCD(
            CdaGeneratorConstants.CODE_EL_NAME,
            CdaGeneratorConstants.CAREPLAN_SEC_CODE,
            CdaGeneratorConstants.LOINC_CODESYSTEM_OID,
            CdaGeneratorConstants.LOINC_CODESYSTEM_NAME,
            CdaGeneratorConstants.CAREPLAN_SEC_NAME));

    // Add Title
    sb.append(
        CdaGeneratorUtils.getXmlForText(
            CdaGeneratorConstants.TITLE_EL_NAME, CdaGeneratorConstants.CAREPLAN_SEC_TITLE));

    // Add Narrative Text
    sb.append(
        CdaGeneratorUtils.getXmlForText(
            CdaGeneratorConstants.TEXT_EL_NAME, "No Plan Of Treatment Information"));

    // Complete the section end tags.
    sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.SECTION_EL_NAME));
    sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.COMP_EL_NAME));

    return sb.toString();
  }

  public static List<DiagnosticReport> getValidDiagnosticOrders(R4FhirData data) {

    List<DiagnosticReport> drs = new ArrayList<>();

    if (data.getDiagReports() != null && !data.getDiagReports().isEmpty()) {

      logger.info(
          "Total num of Diagnostic Reports available for Patient {}", data.getDiagReports().size());

      for (DiagnosticReport dr : data.getDiagReports()) {

        if (dr.getCode() != null
            && dr.getCode().getCoding() != null
            && !dr.getCode().getCoding().isEmpty()
            && (dr.getResult() == null || dr.getResult().isEmpty())
            && Boolean.TRUE.equals(
                CdaFhirUtilities.isCodingPresentForCodeSystem(
                    dr.getCode().getCoding(), CdaGeneratorConstants.FHIR_LOINC_URL))) {

          logger.debug("Found a DiagnosticReport with a LOINC code with no result");
          drs.add(dr);
        } else {
          logger.info(
              " Ignoring Diagnostic Report with id {} as it is not having a loinc code ",
              dr.getIdElement().getIdPart());
        }
      }
    } else {
      logger.debug("No Valid DiagnosticReport in the bundle to process");
    }

    return drs;
  }
}
