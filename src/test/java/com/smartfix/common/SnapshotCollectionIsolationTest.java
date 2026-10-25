package com.smartfix.common;

import com.smartfix.technician.dto.UpdateTechnicianProfileCommand;
import com.smartfix.request.domain.MaintenanceCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/** Prevent callers from changing a response after validation/assembly. */
class SnapshotCollectionIsolationTest {
    static Stream<Class<?>> snapshots() {
        return Stream.of(
                com.smartfix.community.dto.CommunityQuestionDetailResponse.class,
                com.smartfix.dispatch.dto.DispatchPageResponse.class,
                com.smartfix.facility.dto.CampusBuilding.class,
                com.smartfix.facility.dto.CampusGeography.class,
                com.smartfix.facility.dto.CampusMapBuildingResponse.class,
                com.smartfix.facility.dto.CampusMapResponse.class,
                com.smartfix.reporting.dto.CampusMapSnapshot.class,
                com.smartfix.reporting.dto.CampusMapSnapshot.Building.class,
                com.smartfix.reporting.dto.OperationalReportResponse.class,
                com.smartfix.request.dto.MaintenanceRequestDetailsResponse.class,
                com.smartfix.request.dto.RequestPresentationResponse.class,
                com.smartfix.request.service.AttachmentInspectionService.Inspection.class,
                com.smartfix.sla.service.SlaPolicyService.ReadResult.class);
    }

    @ParameterizedTest
    @MethodSource("snapshots")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void sourceMutationCannotChangeAResponseAndAccessorsAreReadOnly(Class<?> type) throws Exception {
        RecordComponent[] components = type.getRecordComponents();
        Object[] input = arguments(components, true);
        Object response = type.getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new)).newInstance(input);
        for (int index = 0; index < components.length; index++) {
            Object source = input[index];
            Object value = components[index].getAccessor().invoke(response);
            if (source instanceof Collection collection) {
                collection.add(null); // Must not appear in the assembled response.
                assertThat((Collection) value).isEmpty();
                assertThatThrownBy(() -> ((Collection) value).add(null)).isInstanceOf(UnsupportedOperationException.class);
            } else if (source instanceof Map map) {
                map.put(null, null);
                assertThat((Map) value).isEmpty();
                assertThatThrownBy(() -> ((Map) value).put(null, null)).isInstanceOf(UnsupportedOperationException.class);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("snapshots")
    void existingNullCollectionSemanticsArePreserved(Class<?> type) throws Exception {
        RecordComponent[] components = type.getRecordComponents();
        Object response = type.getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new)).newInstance(arguments(components, false));
        for (RecordComponent component : components) {
            if (Collection.class.isAssignableFrom(component.getType()) || Map.class.isAssignableFrom(component.getType())) {
                assertThat(component.getAccessor().invoke(response)).isNull();
            }
        }
    }

    private static Object[] arguments(RecordComponent[] components, boolean collections) {
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            Class<?> type = components[i].getType();
            if (type == boolean.class) values[i] = false;
            else if (type == long.class) values[i] = 0L;
            else if (type == int.class) values[i] = 0;
            else if (type == double.class) values[i] = 0.0;
            else if (collections && type == List.class) values[i] = new ArrayList<>();
            else if (collections && type == Set.class) values[i] = new LinkedHashSet<>();
            else if (collections && type == Map.class) values[i] = new LinkedHashMap<>();
        }
        return values;
    }

    @Test
    void formSetInputsAndGettersDoNotShareMutableState() {
        var command = new UpdateTechnicianProfileCommand();
        var skills = new LinkedHashSet<>(Set.of(MaintenanceCategory.ELECTRICAL));
        var areas = new LinkedHashSet<>(Set.of(1L));
        command.setSkills(skills); command.setServiceAreaIds(areas);
        skills.clear(); areas.clear();
        assertThat(command.getSkills()).containsExactly(MaintenanceCategory.ELECTRICAL);
        assertThat(command.getServiceAreaIds()).containsExactly(1L);
        command.getSkills().clear(); command.getServiceAreaIds().clear();
        assertThat(command.getSkills()).hasSize(1);
        assertThat(command.getServiceAreaIds()).hasSize(1);
        command.setSkills(null); command.setServiceAreaIds(null);
        assertThat(command.getSkills()).isNull(); assertThat(command.getServiceAreaIds()).isNull();
    }
}
