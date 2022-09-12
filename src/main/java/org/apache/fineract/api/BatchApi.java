package org.apache.fineract.api;

import org.apache.fineract.file.FileTransferService;
import org.apache.fineract.operations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1")
public class BatchApi {
    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    @Qualifier("awsStorage")
    private FileTransferService fileTransferService;

    @Value("${application.bucket-name}")
    private String bucketName;


    @GetMapping("/batch")
    public BatchDTO batchDetails(@RequestParam(value = "batchId", required = false) String batchId,
                                 @RequestParam(value = "requestId", required = false) String requestId) {
        Batch batch = batchRepository.findByBatchId(batchId);
        if (batch != null) {
            if (batch.getResultGeneratedAt() != null) {
//                Checks if last status was checked before 10 mins
                if (new Date().getTime() - batch.getResultGeneratedAt().getTime() < 600000) {
                    return generateDetails(batch);
                } else {
                    return generateDetails(batch);
                }
            } else {
                return generateDetails(batch);
            }
        } else {
            return null;
        }

    }

    @GetMapping("/batch/detail")
    public ResponseEntity<List<Transfer>> batchDetails(@RequestParam(value = "batchId") String batchId,
                                                       @RequestParam(value = "status", defaultValue = "ALL") String status,
                                                       @RequestParam(value = "pageNo", defaultValue = "0") Integer pageNo,
                                                       @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        List<Transfer> transfers;

        if (status.equalsIgnoreCase(TransferStatus.COMPLETED.toString()) ||
                status.equalsIgnoreCase(TransferStatus.IN_PROGRESS.toString()) ||
                status.equalsIgnoreCase(TransferStatus.FAILED.toString())) {
            transfers = transferRepository.findAllByBatchIdAndStatus(batchId, status.toUpperCase(), PageRequest.of(pageNo, pageSize));
        } else {
            transfers = transferRepository.findAllByBatchId(batchId, PageRequest.of(pageNo, pageSize));
        }

        return new ResponseEntity<List<Transfer>>(transfers, new HttpHeaders(), HttpStatus.OK);
    }

    @GetMapping("/batch/transactions")
    public HashMap<String, String> batchTransactionDetails(@RequestParam String batchId) {
        Batch batch = batchRepository.findByBatchId(batchId);
        if (batch != null) {
            List<Transfer> transfers = transferRepository.findAllByBatchId(batch.getBatchId());
            HashMap<String, String> status = new HashMap<>();
            for(Transfer transfer: transfers){
                status.put(transfer.getTransactionId(), transfer.getStatus().name());
            }
            return status;
        } else {
            return null;
        }
    }

    private BatchDTO generateDetails (Batch batch) {

        List<Transfer> transfers = transferRepository.findAllByBatchId(batch.getBatchId());

        List<Batch> allBatches = batchRepository.findAllByBatchId(batch.getBatchId());

        Long completed = 0L;
        Long failed = 0L;
        Long total = 0L;
        Long ongoing = 0L;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal completedAmount = BigDecimal.ZERO;
        BigDecimal ongoingAmount = BigDecimal.ZERO;
        BigDecimal failedAmount = BigDecimal.ZERO;

        for(int i=0; i<transfers.size(); i++) {
            total++;
            BigDecimal amount = transfers.get(i).getAmount();
            totalAmount = totalAmount.add(amount);
            if (transfers.get(i).getStatus().equals(TransferStatus.COMPLETED)) {
                completed++;
                completedAmount = completedAmount.add(amount);
            } else if (transfers.get(i).getStatus().equals(TransferStatus.FAILED)) {
                failed++;
                failedAmount = failedAmount.add(amount);
            } else if (transfers.get(i).getStatus().equals(TransferStatus.IN_PROGRESS)) {
                ongoing++;
                ongoingAmount = ongoingAmount.add(amount);
            }
        }

        // calculating matrices for sub batches
        AtomicReference<Long> subBatchFailed = new AtomicReference<>(0L);
        AtomicReference<Long> subBatchCompleted = new AtomicReference<>(0L);
        AtomicReference<Long> subBatchOngoing = new AtomicReference<>(0L);
        AtomicReference<Long> subBatchTotal = new AtomicReference<>(0L);
        allBatches.forEach(bt -> {
            if (bt.getSubBatchId() == null || bt.getSubBatchId().isEmpty()) {
                return;
            }
            if (bt.getFailed() != null) {
                subBatchFailed.updateAndGet(v -> v + bt.getFailed());
            }
            if (bt.getCompleted() != null) {
                subBatchCompleted.updateAndGet(v -> v + bt.getCompleted());
            }
            if (bt.getOngoing() != null) {
                subBatchOngoing.updateAndGet(v -> v + bt.getOngoing());
            }
            if (bt.getTotalTransactions() != null) {
                subBatchTotal.updateAndGet(v -> v + bt.getTotalTransactions());
            }
        });

        // updating the data with sub batches details
        completed += subBatchCompleted.get();
        failed += subBatchFailed.get();
        total += subBatchTotal.get();
        ongoing += subBatchOngoing.get();

        batch.setResult_file(createDetailsFile(transfers));
        batch.setCompleted(completed);
        batch.setFailed(failed);
        batch.setResultGeneratedAt(new Date());
        batch.setOngoing(ongoing);
        batch.setTotalTransactions(total);
        batchRepository.save(batch);

        return new BatchDTO(batch.getBatchId(),
                batch.getRequestId(), batch.getTotalTransactions(), batch.getOngoing(),
                batch.getFailed(), batch.getCompleted(), totalAmount, completedAmount,
                ongoingAmount, failedAmount, batch.getResult_file(), batch.getResultGeneratedAt(), batch.getNote());
    }

//    private BatchDTO transformBatchResponse(Batch batch) {
//        return new BatchDTO(batch.getBatchId(), batch.getRequestId(), batch.getTotalTransactions(), batch.getOngoing(), batch.getFailed(), batch.getCompleted(), batch.getResult_file(), batch.getResultGeneratedAt(), batch.getNote());
//    }

    private String createDetailsFile(List<Transfer> transfers) {
        String CSV_SEPARATOR = ",";
        File tempFile = new File(System.currentTimeMillis() + "_response.csv");
        try (
                FileWriter writer = new FileWriter(tempFile.getName());
                BufferedWriter bw = new BufferedWriter(writer)) {
            for (Transfer transfer : transfers)
            {
                StringBuffer oneLine = new StringBuffer();
                oneLine.append(transfer.getTransactionId());
                oneLine.append(CSV_SEPARATOR);
                oneLine.append(transfer.getStatus().toString());
                oneLine.append(CSV_SEPARATOR);
                oneLine.append(transfer.getPayeeDfspId());
                oneLine.append(CSV_SEPARATOR);
                oneLine.append(transfer.getPayeePartyId());
                oneLine.append(CSV_SEPARATOR);
                oneLine.append(transfer.getPayerDfspId());
                oneLine.append(CSV_SEPARATOR);
                oneLine.append(transfer.getPayerPartyId());
                oneLine.append(CSV_SEPARATOR);
                oneLine.append(transfer.getAmount().toString());
                oneLine.append(CSV_SEPARATOR);
                oneLine.append(transfer.getCurrency());
                oneLine.append(CSV_SEPARATOR);
                oneLine.append(transfer.getErrorInformation());
                oneLine.append(CSV_SEPARATOR);
                oneLine.append(transfer.getStartedAt().toString());
                oneLine.append(CSV_SEPARATOR);
                oneLine.append(transfer.getCompletedAt().toString());
                oneLine.append(CSV_SEPARATOR);
                bw.write(oneLine.toString());
                bw.newLine();
            }
            bw.flush();
            return fileTransferService.uploadFile(tempFile, bucketName);
        } catch (Exception e) {
            System.err.format("Exception: %s%n", e);
        }
        return null;
    }

}
