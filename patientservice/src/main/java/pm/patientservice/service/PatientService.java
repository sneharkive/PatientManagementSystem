package pm.patientservice.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import pm.patientservice.dto.PatientRequestDTO;
import pm.patientservice.dto.PatientResponseDTO;
import pm.patientservice.exception.EmailAlreadyExistsException;
import pm.patientservice.exception.PatientNotFoundException;
import pm.patientservice.grpc.BillingServiceGrpcClient;
import pm.patientservice.kafka.KafkaProducer;
import pm.patientservice.mapper.PatientMapper;
import pm.patientservice.model.Patient;
import pm.patientservice.repository.PatientRepository;

@Service
public class PatientService {

  private final KafkaProducer kafkaProducer;

  private final PatientRepository patientRepository;
  private final BillingServiceGrpcClient billingServiceGrpcClient;

  public PatientService(PatientRepository patientRepository, BillingServiceGrpcClient billingServiceGrpcClient, KafkaProducer kafkaProducer){
    this.patientRepository = patientRepository;
    this.billingServiceGrpcClient = billingServiceGrpcClient;
    this.kafkaProducer = kafkaProducer;
  }

  public List<PatientResponseDTO> getPatients(){
    List<Patient> patients = patientRepository.findAll();

    List<PatientResponseDTO> patientResponseDTO = patients.stream().map(patient -> PatientMapper.toDTO(patient)).toList();
    return patientResponseDTO;

  }

  public PatientResponseDTO createPatient(PatientRequestDTO patientRequestDTO){
    if (patientRepository.existsByEmail(patientRequestDTO.getEmail())) {
      throw new EmailAlreadyExistsException(
          "A patient with this email " + "already exists"
              + patientRequestDTO.getEmail());
    }

    Patient newPatient = patientRepository.save(PatientMapper.toModel(patientRequestDTO));

    billingServiceGrpcClient.createBillingAccount(newPatient.getId(), newPatient.getName(), newPatient.getEmail());

    kafkaProducer.sendEvent(newPatient);

    return PatientMapper.toDTO(newPatient);
  }

   public PatientResponseDTO updatePatient(UUID id, PatientRequestDTO patientRequestDTO) {

    Patient patient = patientRepository.findById(id).orElseThrow(
        () -> new PatientNotFoundException("Patient not found with ID: " + id));

    if (patientRepository.existsByEmailAndIdNot(patientRequestDTO.getEmail(),
        id)) {
      throw new EmailAlreadyExistsException(
          "A patient with this email " + "already exists"
              + patientRequestDTO.getEmail());
    }

    patient.setName(patientRequestDTO.getName());
    patient.setAddress(patientRequestDTO.getAddress());
    patient.setEmail(patientRequestDTO.getEmail());
    patient.setDateOfBirth(LocalDate.parse(patientRequestDTO.getDateOfBirth()));

    Patient updatedPatient = patientRepository.save(patient);
    return PatientMapper.toDTO(updatedPatient);
  }

  public void deletePatient(UUID id) {
    patientRepository.deleteById(id);
  }

}
