import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaltech.monitoramento.api.dto.*;
import com.metaltech.monitoramento.api.mapper.*;
import com.metaltech.monitoramento.domain.model.*;
import java.time.Instant;
import java.util.*;

public class TestJson {
  public static void main(String[] args) throws Exception {
    Instant agora = Instant.now();
    LeituraSensores validEntity = new LeituraSensores(1L, agora, 12.5, true, 0.85, false, 0.12, TipoPeca.METALICA, 75.5, StatusReservatorio.NORMAL, "ESP32-001", "1.0.0", Collections.emptyList());
    LeituraSensoresMapper mapper = new LeituraSensoresMapper();
    LeituraSensoresResponseDTO dto = mapper.toResponseDto(validEntity);
    System.out.println(dto.getAlertas());
    System.out.println(new ObjectMapper().writeValueAsString(dto));
    Map<String,Object> response = new LinkedHashMap<>();
    response.put("content", List.of(dto));
    response.put("pageable", Map.of("pageNumber", 0, "pageSize", 10, "sort", "UNSORTED", "offset", 0L));
    System.out.println(new ObjectMapper().writeValueAsString(response));
  }
}
