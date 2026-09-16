package cl.pedidos360.report.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, Long> {

    List<OrderSnapshot> findByCurrentStatus(String status);
}
