package org.opendatakit.briefcase.ui.export;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.opendatakit.briefcase.model.BriefcaseFormDefinition;
import org.opendatakit.briefcase.model.BriefcasePreferences;
import org.opendatakit.briefcase.model.FormStatus;
import org.opendatakit.briefcase.model.IFormDefinition;

public class ExportForms {
  private static final String EXPORT_DATE_PREFIX = "export_date_";
  private static final String CUSTOM_CONF_PREFIX = "custom_";
  private final List<FormStatus> forms;
  private final Map<FormStatus, ExportConfiguration> configurations;
  private final Map<FormStatus, LocalDateTime> lastExportDateTimes;
  private final List<BiConsumer<FormStatus, LocalDateTime>> onSuccessfulExportCallbacks = new ArrayList<>();
  private Map<String, FormStatus> formsIndex = new HashMap<>();

  public ExportForms(List<FormStatus> forms, Map<FormStatus, ExportConfiguration> configurations, Map<FormStatus, LocalDateTime> lastExportDateTimes) {
    this.forms = forms;
    this.configurations = configurations;
    this.lastExportDateTimes = lastExportDateTimes;
    rebuildIndex();
  }

  public static ExportForms load(List<FormStatus> forms, BriefcasePreferences preferences) {
    // This should be a simple Map filtering block but we'll have to wait for Vavr.io

    Map<FormStatus, ExportConfiguration> configurations = new HashMap<>();
    Map<FormStatus, LocalDateTime> lastExportDateTimes = new HashMap<>();
    forms.forEach(form -> {
      configurations.put(form, ExportConfiguration.load(preferences, buildCustomConfPrefix(form)));
      preferences.nullSafeGet(buildExportDateTimePrefix(form))
          .map(LocalDateTime::parse).ifPresent(dateTime -> lastExportDateTimes.put(form, dateTime));
    });
    return new ExportForms(
        forms,
        configurations,
        lastExportDateTimes
    );
  }

  static String buildExportDateTimePrefix(FormStatus form) {
    IFormDefinition formDefinition = form.getFormDefinition();
    return buildExportDateTimePrefix(formDefinition);
  }

  public static String buildExportDateTimePrefix(IFormDefinition formDefinition) {
    return EXPORT_DATE_PREFIX + formDefinition.getFormId();
  }

  static String buildCustomConfPrefix(FormStatus form) {
    return CUSTOM_CONF_PREFIX + form.getFormName() + "_";
  }

  public void merge(List<FormStatus> forms) {
    this.forms.addAll(forms.stream().filter(form -> !formsIndex.containsKey(form.getFormDefinition().getFormId())).collect(toList()));
    rebuildIndex();
  }

  public int size() {
    return forms.size();
  }

  public FormStatus get(int rowIndex) {
    return forms.get(rowIndex);
  }

  public boolean hasConfiguration(FormStatus form) {
    return configurations.containsKey(form);
  }

  public ExportConfiguration getConfiguration(FormStatus form) {
    return configurations.computeIfAbsent(form, ___ -> ExportConfiguration.empty());
  }

  public Optional<ExportConfiguration> getConfiguration(BriefcaseFormDefinition formDefinition) {
    return Optional.ofNullable(configurations.get(getForm(formDefinition)));
  }

  public void removeConfiguration(FormStatus form) {
    configurations.remove(form);
  }

  public void setConfiguration(FormStatus form, ExportConfiguration configuration) {
    configurations.put(form, configuration);
  }

  public void selectAll() {
    forms.forEach(form -> form.setSelected(true));
  }

  public void clearAll() {
    forms.forEach(form -> form.setSelected(false));
  }

  public List<FormStatus> getSelectedForms() {
    return forms.stream().filter(FormStatus::isSelected).collect(toList());
  }

  public boolean someSelected() {
    return !getSelectedForms().isEmpty();
  }

  public boolean allSelected() {
    return forms.stream().allMatch(FormStatus::isSelected);
  }

  public boolean noneSelected() {
    return forms.stream().noneMatch(FormStatus::isSelected);
  }

  public boolean allSelectedFormsHaveConfiguration() {
    return getSelectedForms().stream().allMatch(form -> configurations.containsKey(form) && !configurations.get(form).isEmpty());
  }

  public void appendStatus(BriefcaseFormDefinition formDefinition, String statusUpdate, boolean successful) {
    FormStatus form = getForm(formDefinition);
    form.setStatusString(statusUpdate, successful);
    if (successful) {
      LocalDateTime exportDate = LocalDateTime.now();
      lastExportDateTimes.put(form, exportDate);
      onSuccessfulExportCallbacks.forEach(callback -> callback.accept(form, exportDate));
    }
  }

  public Map<FormStatus, ExportConfiguration> getValidConfigurations() {
    return configurations.entrySet().stream()
        .filter(entry -> !entry.getValue().isEmpty() && entry.getValue().isValid())
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Optional<LocalDateTime> getLastExportDateTime(FormStatus form) {
    return Optional.ofNullable(lastExportDateTimes.get(form));
  }

  public void onSuccessfulExport(BiConsumer<FormStatus, LocalDateTime> callback) {
    onSuccessfulExportCallbacks.add(callback);
  }

  private FormStatus getForm(BriefcaseFormDefinition formDefinition) {
    return Optional.ofNullable(formsIndex.get(formDefinition.getFormId()))
        .orElseThrow(() -> new RuntimeException("Form " + formDefinition.getFormName() + " " + formDefinition.getFormId() + " not found"));
  }

  private void rebuildIndex() {
    formsIndex = forms.stream().collect(toMap(form -> form.getFormDefinition().getFormId(), form -> form));
  }
}
