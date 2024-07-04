
package org.isf.vaccine.restAssured;

import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import jakarta.servlet.ServletException;
import org.isf.shared.exceptions.OHResponseEntityExceptionHandler;
import org.isf.shared.mapper.converter.BlobToByteArrayConverter;
import org.isf.shared.mapper.converter.ByteArrayToBlobConverter;
import org.isf.vaccine.data.VaccineHelper;
import org.isf.vaccine.dto.VaccineDTO;
import org.isf.vaccine.manager.VaccineBrowserManager;
import org.isf.vaccine.mapper.VaccineMapper;
import org.isf.vaccine.model.Vaccine;
import org.isf.vaccine.rest.VaccineController;
import org.isf.vactype.model.VaccineType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

public class VaccineControllerTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(VaccineControllerTest.class);

	@Mock
	protected VaccineBrowserManager vaccineBrowserManagerMock;

	protected VaccineMapper vaccineMapper = new VaccineMapper();

	private MockMvc mockMvc;

	private AutoCloseable closeable;

	private List<Vaccine> vaccinesList = new ArrayList<Vaccine>();
	private List<VaccineDTO> expectedVaccineDTOs = new ArrayList<VaccineDTO>();

	@BeforeEach
	public void setup() {
		closeable = MockitoAnnotations.openMocks(this);
		this.mockMvc = MockMvcBuilders
			.standaloneSetup(new VaccineController(vaccineBrowserManagerMock, vaccineMapper))
			.setControllerAdvice(new OHResponseEntityExceptionHandler())
			.build();
		ModelMapper modelMapper = new ModelMapper();
		modelMapper.addConverter(new BlobToByteArrayConverter());
		modelMapper.addConverter(new ByteArrayToBlobConverter());
		ReflectionTestUtils.setField(vaccineMapper, "modelMapper", modelMapper);
	}

	@AfterEach
	void closeService() throws Exception {
		closeable.close();
	}

	public void v_NoVaccines() throws Exception {
		System.out.println("Executing: v_NoVaccines");

		String request = "/vaccines";
		RestAssuredMockMvc.given()
			.mockMvc(this.mockMvc)
			.contentType(ContentType.JSON)
			.when()
			.get(request)
			.then()
			.statusCode(HttpStatus.NO_CONTENT.value())
			.body(containsString("[]"));
	}

	public void v_Vaccines() throws Exception {
		System.out.println("Executing: v_Vaccines");

		String request = "/vaccines";
		when(vaccineBrowserManagerMock.getVaccine())
			.thenReturn(vaccinesList);

		expectedVaccineDTOs = vaccineMapper.map2DTOList(vaccinesList);

		var res = RestAssuredMockMvc.given().mockMvc(this.mockMvc)
			.contentType(ContentType.JSON)
			.when()
			.get(request)
			.then()
			.statusCode(HttpStatus.OK.value())
			.body(containsString(VaccineHelper.getObjectMapper().writeValueAsString(expectedVaccineDTOs)));

	}

	public void e_NewVaccine() throws Exception {
		System.out.println("Executing: e_NewVaccine");

		String request = "/vaccines";

		String code = "Z" + this.vaccinesList.size();
		Vaccine vaccine = VaccineHelper.setup(code);
		VaccineDTO body = vaccineMapper.map2DTO(vaccine);

		this.vaccinesList.add(vaccine);
		this.expectedVaccineDTOs.add(body);

		when(vaccineBrowserManagerMock.newVaccine(vaccineMapper.map2Model(body)))
			.thenReturn(vaccine);

		RestAssuredMockMvc.given()
			.mockMvc(this.mockMvc)
			.contentType(MediaType.APPLICATION_JSON_VALUE)
			.body(VaccineHelper.asJsonString(body))
			.when()
			.post(request)
			.then()
			.statusCode(HttpStatus.CREATED.value());
	}

	public void e_NewVaccineException() throws Exception {
		System.out.println("Executing: e_NewVaccineException");

		String request = "/vaccines";
		String code = "Z" + this.vaccinesList.size();
		Vaccine vaccine = VaccineHelper.setup(code);
		vaccine.setVaccineType(setupVaccineType(null));
		VaccineDTO body = vaccineMapper.map2DTO(vaccine);

		this.vaccinesList.add(vaccine);
		this.expectedVaccineDTOs.add(body);

		when(vaccineBrowserManagerMock.newVaccine(vaccineMapper.map2Model(body)))
			.thenReturn(vaccine);

		assertThrows(ServletException.class, () -> {
			RestAssuredMockMvc.given()
				.mockMvc(this.mockMvc)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.body(VaccineHelper.asJsonString(body))
				.when()
				.post(request);
		});

	}

	public void e_DeleteVaccine() throws Exception {
		System.out.println("Executing: e_DeleteVaccine");

		String request = "/vaccines/{code}";

		Vaccine vaccine = this.vaccinesList.get(this.vaccinesList.size() - 1);
		VaccineDTO body = vaccineMapper.map2DTO(vaccine);
		String code = body.getCode();

		this.vaccinesList.remove(vaccine);
		this.expectedVaccineDTOs.remove(body);

		when(vaccineBrowserManagerMock.findVaccine(code))
			.thenReturn(vaccine);

		String isDeleted = "true";

		RestAssuredMockMvc.given()
			.mockMvc(this.mockMvc)
			.when()
			.delete(request, code)
			.then()
			.log().all()
			.statusCode(HttpStatus.OK.value())
			.body(equalTo(isDeleted));

	}

	public void e_DeleteVaccineException() throws Exception {
		System.out.println("Executing: e_DeleteVaccineException");

		String request = "/vaccines/{code}";

		Vaccine vaccine = this.setupTestVaccine("New-Code");
		VaccineDTO body = vaccineMapper.map2DTO(vaccine);
		String code = body.getCode();

		when(vaccineBrowserManagerMock.findVaccine(code))
			.thenReturn(vaccine);

		String isDeleted = "false";
		assertThrows(AssertionError.class, () -> {
			RestAssuredMockMvc.given()
				.mockMvc(this.mockMvc)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.when()
				.delete(request, code)
				.then()
				.body(equalTo(isDeleted));
		});

	}

	public void e_UpdateVaccine() throws Exception {
		System.out.println("Executing: e_UpdateVaccine");

		String request = "/vaccines";

		Vaccine vaccine = this.vaccinesList.get(0);
		vaccine.setDescription("New-description");
		VaccineDTO body = vaccineMapper.map2DTO(vaccine);

		when(vaccineBrowserManagerMock.updateVaccine(vaccineMapper.map2Model(body)))
			.thenReturn(vaccine);

		RestAssuredMockMvc.given()
			.mockMvc(this.mockMvc)
			.contentType(MediaType.APPLICATION_JSON_VALUE)
			.body(VaccineHelper.asJsonString(body))
			.when()
			.put(request)
			.then()
			.statusCode(HttpStatus.OK.value());

	}

	public void e_UpdateVaccineException() throws Exception {
		System.out.println("Executing: e_UpdateVaccineException");

		String request = "/vaccines";

		Vaccine vaccine;
		if (this.vaccinesList.isEmpty()) {
			vaccine = setupTestVaccine("New-Code");
		} else {
			vaccine = this.vaccinesList.get(0);
		}
		vaccine.setCode("Very-Very-Very-Very-Very-Very-Very");
		VaccineDTO body = vaccineMapper.map2DTO(vaccine);

		when(vaccineBrowserManagerMock.updateVaccine(vaccineMapper.map2Model(body)))
			.thenReturn(vaccine);

		assertThrows(ServletException.class, () -> {
			RestAssuredMockMvc.given()
				.mockMvc(this.mockMvc)
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.body(VaccineHelper.asJsonString(body))
				.when()
				.put(request)
				.then()
				.statusCode(HttpStatus.OK.value());
		});
	}

	public void e_FindVaccine() throws Exception {
		System.out.println("Executing: e_FindVaccine");

		String req = "/vaccines";
		Vaccine vaccine;
		if (this.vaccinesList.isEmpty()) {
			vaccine = setupTestVaccine("New-Code");
		} else {
			vaccine = this.vaccinesList.get(this.vaccinesList.size() - 1);
		}

		VaccineDTO body = vaccineMapper.map2DTO(vaccine);
		String code = body.getCode();

		when(vaccineBrowserManagerMock.findVaccine(code))
			.thenReturn(vaccine);

		RestAssuredMockMvc.given().mockMvc(this.mockMvc)
			.contentType(ContentType.JSON)
			.when()
			.get(req)
			.then()
			.extract();
	}

	public void e_FindVaccineException() throws Exception {
		System.out.println("Executing: e_FindVaccineException");

		String req = "/vaccines";
		Vaccine vaccine;
		if (this.vaccinesList.isEmpty()) {
			vaccine = setupTestVaccine("New-Code");
		} else {
			vaccine = this.vaccinesList.get(this.vaccinesList.size() - 1);
		}
		VaccineDTO body = vaccineMapper.map2DTO(vaccine);
		String code = body.getCode();

		when(vaccineBrowserManagerMock.findVaccine(code))
			.thenReturn(vaccine);

		RestAssuredMockMvc.given().mockMvc(this.mockMvc)
			.contentType(ContentType.JSON)
			.when()
			.get(req)
			.then()
			.extract();
	}

	public void e_IsCodePresent() throws Exception {
		System.out.println("Executing: e_IsCodePresent");

		String request = "/vaccines/check/{code}";

		Vaccine vaccine;
		boolean isPresent;
		if (this.vaccinesList.isEmpty()) {
			vaccine = setupTestVaccine("New-Code");
			isPresent = false;
		} else {
			vaccine = this.vaccinesList.get(this.vaccinesList.size() - 1);
			isPresent = true;
		}

		VaccineDTO body = vaccineMapper.map2DTO(vaccine);
		String code = body.getCode();

		when(vaccineBrowserManagerMock.isCodePresent(vaccine.getCode()))
			.thenReturn(isPresent);

		RestAssuredMockMvc.given()
			.mockMvc(this.mockMvc)
			.when()
			.get(request, code)
			.then()
			.log().all()
			.statusCode(HttpStatus.OK.value())
			.body(equalTo(Boolean.toString(isPresent)));

	}

	public void e_IsCodePresentException() throws Exception {
		System.out.println("Executing: e_IsCodePresentException");

		String request = "/vaccines/check/{code}";

		String code = null;

		when(vaccineBrowserManagerMock.isCodePresent(code))
			.thenReturn(false);

		assertThrows(NullPointerException.class, () -> {
			RestAssuredMockMvc.given()
				.mockMvc(this.mockMvc)
				.when()
				.get(request, code)
				.then()
				.log().all()
				.statusCode(HttpStatus.OK.value())
				.body(equalTo(Boolean.toString(false)));
		});

	}

	public void v_OHException() {
		System.out.println("Executing: v_OHException");
	}

	public void e_GetVaccine() throws Exception {
		System.out.println("Executing: e_GetVaccine");

		String request = "/vaccines";
		var statusCode = HttpStatus.OK.value();
		when(vaccineBrowserManagerMock.getVaccine())
			.thenReturn(vaccinesList);
		if (this.vaccinesList.isEmpty()) {
			statusCode = HttpStatus.NO_CONTENT.value();
		}

		expectedVaccineDTOs = vaccineMapper.map2DTOList(vaccinesList);

		RestAssuredMockMvc.given().mockMvc(this.mockMvc)
			.contentType(ContentType.JSON)
			.when()
			.get(request)
			.then()
			.statusCode(statusCode)
			.body(containsString(VaccineHelper.getObjectMapper().writeValueAsString(expectedVaccineDTOs)));

	}

	public void e_Reset() throws Exception {
		System.out.println("Executing: e_Reset");

		this.vaccinesList = new ArrayList<Vaccine>();
		this.expectedVaccineDTOs = new ArrayList<VaccineDTO>();
		this.closeService();
		this.setup();

	}


	@Test
	public void testCase1() throws Exception {
		v_NoVaccines();
		e_IsCodePresentException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase2() throws Exception {
		v_NoVaccines();
		e_DeleteVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase3() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_DeleteVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase4() throws Exception {
		v_NoVaccines();
		e_NewVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase5() throws Exception {
		v_NoVaccines();
		e_IsCodePresent();
		v_NoVaccines();
	}

	@Test
	public void testCase6() throws Exception {
		v_NoVaccines();
		e_UpdateVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase7() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_FindVaccine();
		v_Vaccines();
		e_FindVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase8() throws Exception {
		v_NoVaccines();
		e_FindVaccine();
		v_NoVaccines();
	}

	@Test
	public void testCase9() throws Exception {
		v_NoVaccines();
		e_GetVaccine();
		v_NoVaccines();
	}

	@Test
	public void testCase10() throws Exception {
		v_NoVaccines();
		e_FindVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase11() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_GetVaccine();
		v_Vaccines();
		e_DeleteVaccine();
		v_NoVaccines();
	}

	@Test
	public void testCase12() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_NewVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase13() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_IsCodePresentException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase14() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_NewVaccine();
		v_Vaccines();
		e_FindVaccine();
		v_Vaccines();
		e_FindVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase15() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_UpdateVaccine();
		v_Vaccines();
		e_FindVaccine();
		v_Vaccines();
		e_DeleteVaccine();
		v_NoVaccines();
	}


	@Test
	public void testCase16() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_GetVaccine();
		v_Vaccines();
		e_NewVaccine();
		v_Vaccines();
		e_FindVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase17() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_UpdateVaccine();
		v_Vaccines();
		e_DeleteVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase18() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_FindVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase19() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_NewVaccine();
		v_Vaccines();
		e_FindVaccine();
		v_Vaccines();
		e_IsCodePresent();
		v_Vaccines();
		e_FindVaccine();
		v_Vaccines();
		e_UpdateVaccine();
		v_Vaccines();
		e_FindVaccine();
		v_Vaccines();
		e_DeleteVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase20() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_UpdateVaccine();
		v_Vaccines();
		e_UpdateVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}


	@Test
	public void testCase21() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_GetVaccine();
		v_Vaccines();
		e_NewVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase22() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_UpdateVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase23() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_DeleteVaccine();
		v_NoVaccines();
	}

	@Test
	public void testCase24() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_FindVaccine();
		v_Vaccines();
		e_NewVaccine();
		v_Vaccines();
		e_UpdateVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}


	@Test
	public void testCase25() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_NewVaccine();
		v_Vaccines();
		e_NewVaccine();
		v_Vaccines();
		e_GetVaccine();
		v_Vaccines();
		e_FindVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}


	@Test
	public void testCase26() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_GetVaccine();
		v_Vaccines();
		e_NewVaccine();
		v_Vaccines();
		e_GetVaccine();
		v_Vaccines();
		e_FindVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}


	@Test
	public void testCase27() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_GetVaccine();
		v_Vaccines();
		e_IsCodePresent();
		v_Vaccines();
		e_NewVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase28() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_GetVaccine();
		v_Vaccines();
		e_FindVaccineException();
		v_OHException();
		e_Reset();
		v_NoVaccines();
	}

	@Test
	public void testCase29() throws Exception {
		v_NoVaccines();
		e_NewVaccine();
		v_Vaccines();
		e_NewVaccine();
		v_Vaccines();
		e_DeleteVaccine();
		v_Vaccines();
		e_DeleteVaccine();
		v_NoVaccines();
	}

	private static VaccineType setupVaccineType(String vaccineTypeCode) {
		return new VaccineType(vaccineTypeCode, "Default Description");
	}

	private Vaccine setupTestVaccine(String vaccineCode) {
		return new Vaccine(vaccineCode, "Default description", new VaccineType("TT", "Default description"));
	}

}
