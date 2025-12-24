package ru.itmo.isitmolab.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ru.itmo.isitmolab.model.VehicleImportOperation;

import java.util.List;

@ApplicationScoped
public class VehicleImportOperationDao {

    @PersistenceContext
    private EntityManager em;

    public void save(VehicleImportOperation op) {
        em.persist(op);
    }

    // public List<VehicleImportOperation> findLastForAdmin(Long adminId, int limit) {
    //     return em.createQuery(
    //                     "SELECT o " +
    //                             "FROM VehicleImportOperation o " +
    //                             "JOIN FETCH o.admin " +
    //                             "WHERE o.admin.id = :adminId " +
    //                             "ORDER BY o.creationTime DESC",
    //                     VehicleImportOperation.class
    //             )
    //             .setParameter("adminId", adminId)
    //             .setMaxResults(limit)
    //             .getResultList();
    // }
    public List<VehicleImportOperation> findLastForAdmin(int limit) {
        return em.createQuery(
                        "SELECT o " +
                                "FROM VehicleImportOperation o " +
                                "ORDER BY o.creationTime DESC",
                        VehicleImportOperation.class
                )
                .setMaxResults(limit)
                .getResultList();
    }

}
