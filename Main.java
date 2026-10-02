import java.util.*;
import java.util.stream.Collectors;

/**
 * CRUD QUẢN LÝ NHÂN VIÊN - minh hoạ cách chọn List / Set / Map đúng chỗ.
 *
 * Tóm tắt nhanh:
 *  - Map  : tìm theo KEY (id) cực nhanh  -> kho dữ liệu chính
 *  - Set  : đảm bảo KHÔNG TRÙNG          -> email, kỹ năng
 *  - List : giữ THỨ TỰ, cho phép trùng   -> kết quả trả về, sắp xếp, nhật ký
 *
 * Chạy: javac Main.java && java Main
 */

// ===================== MODEL =====================
class Employee {
    private final int id;
    private String name;
    private String email;
    private String department;
    private double salary;

    // SET<String> cho kỹ năng:
    //  - Một nhân viên không thể có kỹ năng "Java" 2 lần -> Set tự loại trùng.
    //  - Dùng LinkedHashSet để vẫn giữ thứ tự thêm vào khi in ra cho đẹp.
    //  Nếu dùng List: phải tự viết if (!skills.contains(x)) mỗi lần thêm, quên 1 chỗ là bị trùng,
    //                 và contains() trên List là O(n) (duyệt từng phần tử).
    //  Nếu dùng HashSet: vẫn không trùng nhưng thứ tự in ra lộn xộn, khó đọc.
    private final Set<String> skills = new LinkedHashSet<>();

    Employee(int id, String name, String email, String department, double salary) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.department = department;
        this.salary = salary;
    }

    int getId() { return id; }
    String getName() { return name; }
    String getEmail() { return email; }
    String getDepartment() { return department; }
    double getSalary() { return salary; }
    Set<String> getSkills() { return skills; }

    void setName(String name) { this.name = name; }
    void setEmail(String email) { this.email = email; }
    void setDepartment(String department) { this.department = department; }
    void setSalary(double salary) { this.salary = salary; }

    @Override
    public String toString() {
        return String.format("#%d | %-12s | %-20s | %-6s | %,.0f | skills=%s",
                id, name, email, department, salary, skills);
    }
}

// ===================== REPOSITORY (CRUD) =====================
class EmployeeRepository {

    // MAP<Integer, Employee> - kho dữ liệu CHÍNH.
    //  - Mọi thao tác Read/Update/Delete đều có sẵn id -> map.get(id) là O(1).
    //  - id là duy nhất, tự nhiên đóng vai trò KEY.
    //  Nếu dùng List<Employee>: mỗi lần tìm/sửa/xoá theo id phải duyệt từ đầu, O(n).
    //     Với 100.000 nhân viên mà tra cứu liên tục thì chậm thấy rõ.
    //  Nếu dùng Set<Employee>: không có cách "lấy ra phần tử có id = 5" ngoài việc duyệt hết,
    //     vì Set chỉ biết "có/không có", không biết "lấy theo khoá".
    //  Dùng LinkedHashMap (thay vì HashMap) để khi liệt kê vẫn theo thứ tự thêm vào.
    private final Map<Integer, Employee> store = new LinkedHashMap<>();

    // SET<String> - chỉ mục email để kiểm tra TRÙNG EMAIL.
    //  - Nghiệp vụ: 2 nhân viên không được dùng chung email.
    //  - emailIndex.contains(email) là O(1) và diễn đạt đúng ý "tập các email đã dùng".
    //  Nếu dùng List<String>: contains() là O(n), và List cho phép trùng nên không tự bảo vệ ràng buộc.
    //  Nếu duyệt store.values() để so email: cũng O(n) mỗi lần thêm/sửa.
    //  Đánh đổi: phải nhớ đồng bộ Set này khi sửa/xoá email (xem update() và delete()).
    //  Chuẩn hoá về chữ thường để "A@x.com" và "a@x.com" được coi là cùng 1 email.
    private final Set<String> emailIndex = new HashSet<>();

    // LIST<String> - nhật ký thao tác (audit log).
    //  - Cần GIỮ ĐÚNG THỨ TỰ thời gian và CHO PHÉP TRÙNG (xoá 2 lần cùng 1 người vẫn ghi 2 dòng).
    //  Nếu dùng Set: mất bản ghi trùng nội dung, và (HashSet) mất luôn thứ tự.
    //  Nếu dùng Map: không có "key" tự nhiên nào cho từng dòng log.
    private final List<String> auditLog = new ArrayList<>();

    private int nextId = 1;

    private String normalize(String email) { return email.trim().toLowerCase(); }

    // ---------- CREATE ----------
    Employee create(String name, String email, String dept, double salary) {
        String key = normalize(email);
        if (emailIndex.contains(key)) { // kiểm tra trùng bằng Set: O(1)
            throw new IllegalArgumentException("Email đã tồn tại: " + email);
        }
        Employee e = new Employee(nextId++, name, key, dept, salary);
        store.put(e.getId(), e);   // lưu vào Map
        emailIndex.add(key);       // đồng bộ chỉ mục Set
        auditLog.add("CREATE #" + e.getId() + " " + name);
        return e;
    }

    // ---------- READ ----------
    // Optional vì Map.get() trả null khi không có -> tránh NullPointerException.
    Optional<Employee> findById(int id) {
        return Optional.ofNullable(store.get(id)); // O(1) nhờ Map
    }

    // Trả về LIST vì người gọi cần danh sách có THỨ TỰ để hiển thị/duyệt/sắp xếp.
    // Trả bản sao (new ArrayList) để bên ngoài không làm hỏng dữ liệu bên trong.
    List<Employee> findAll() {
        return new ArrayList<>(store.values());
    }

    // Kết quả lọc là LIST: có thể rỗng, có thể nhiều phần tử, cần giữ thứ tự.
    List<Employee> findByDepartment(String dept) {
        return store.values().stream()
                .filter(e -> e.getDepartment().equalsIgnoreCase(dept))
                .collect(Collectors.toList());
    }

    // Tìm theo kỹ năng: dùng Set.contains() nên rất gọn và nhanh.
    List<Employee> findBySkill(String skill) {
        return store.values().stream()
                .filter(e -> e.getSkills().contains(skill))
                .collect(Collectors.toList());
    }

    // MAP<String, List<Employee>> - gom nhóm theo phòng ban.
    //  - Key = tên phòng, Value = danh sách nhân viên của phòng đó (quan hệ 1-nhiều).
    //  Nếu dùng List<List<Employee>>: không biết list con nào ứng với phòng nào.
    //  Nếu dùng Set<Employee>: không thể diễn đạt "nhóm theo phòng".
    //  TreeMap để tên phòng được sắp xếp A-Z khi in báo cáo.
    Map<String, List<Employee>> groupByDepartment() {
        return store.values().stream()
                .collect(Collectors.groupingBy(Employee::getDepartment, TreeMap::new, Collectors.toList()));
    }

    // Báo cáo tổng lương theo phòng: Map<String, Double> (key phòng -> 1 con số).
    Map<String, Double> totalSalaryByDepartment() {
        return store.values().stream()
                .collect(Collectors.groupingBy(Employee::getDepartment, TreeMap::new,
                        Collectors.summingDouble(Employee::getSalary)));
    }

    // Sắp xếp: chỉ LIST mới có thứ tự theo chỉ số để sort. Map/Set (Hash*) không sort được tại chỗ.
    List<Employee> findAllSortedBySalaryDesc() {
        List<Employee> list = findAll();
        list.sort(Comparator.comparingDouble(Employee::getSalary).reversed());
        return list;
    }

    // ---------- UPDATE ----------
    void update(int id, String name, String newEmail, String dept, double salary) {
        Employee e = store.get(id);
        if (e == null) throw new NoSuchElementException("Không tìm thấy nhân viên #" + id);

        String newKey = normalize(newEmail);
        // Chỉ kiểm tra trùng khi THỰC SỰ đổi email (nếu giữ nguyên thì tất nhiên đang "trùng với chính mình").
        if (!newKey.equals(e.getEmail())) {
            if (emailIndex.contains(newKey)) {
                throw new IllegalArgumentException("Email đã tồn tại: " + newEmail);
            }
            emailIndex.remove(e.getEmail()); // gỡ email cũ khỏi Set
            emailIndex.add(newKey);          // thêm email mới
            e.setEmail(newKey);
        }
        e.setName(name);
        e.setDepartment(dept);
        e.setSalary(salary);
        auditLog.add("UPDATE #" + id);
    }

    // Thêm kỹ năng: Set.add() trả false nếu đã có -> không cần if kiểm tra thủ công.
    boolean addSkill(int id, String skill) {
        Employee e = store.get(id);
        if (e == null) throw new NoSuchElementException("Không tìm thấy nhân viên #" + id);
        boolean added = e.getSkills().add(skill);
        auditLog.add((added ? "ADD_SKILL #" : "ADD_SKILL(trùng, bỏ qua) #") + id + " " + skill);
        return added;
    }

    // ---------- DELETE ----------
    boolean delete(int id) {
        Employee removed = store.remove(id); // O(1), trả về phần tử đã xoá (hoặc null)
        if (removed == null) return false;
        emailIndex.remove(removed.getEmail()); // QUAN TRỌNG: gỡ khỏi Set, nếu quên thì email bị "khoá chết"
        auditLog.add("DELETE #" + id + " " + removed.getName());
        return true;
    }

    List<String> getAuditLog() { return Collections.unmodifiableList(auditLog); }
}

// ===================== DEMO =====================
public class Main {
    public static void main(String[] args) {
        EmployeeRepository repo = new EmployeeRepository();

        System.out.println("=== CREATE ===");
        Employee an = repo.create("Nguyễn Văn An", "an@corp.vn", "IT", 25_000_000);
        Employee binh = repo.create("Trần Thị Bình", "binh@corp.vn", "HR", 18_000_000);
        Employee cuong = repo.create("Lê Quốc Cường", "cuong@corp.vn", "IT", 30_000_000);
        repo.create("Phạm Minh Dũng", "dung@corp.vn", "Sales", 20_000_000);

        try {
            repo.create("Người Trùng Email", "AN@corp.vn", "IT", 1); // trùng (không phân biệt hoa thường)
        } catch (IllegalArgumentException ex) {
            System.out.println("Bị chặn đúng: " + ex.getMessage());
        }

        repo.addSkill(an.getId(), "Java");
        repo.addSkill(an.getId(), "SQL");
        repo.addSkill(an.getId(), "Java"); // trùng -> Set tự bỏ qua
        repo.addSkill(cuong.getId(), "Java");
        repo.addSkill(binh.getId(), "Tuyển dụng");

        System.out.println("\n=== READ ===");
        repo.findAll().forEach(System.out::println);
        System.out.println("\nTìm #2: " + repo.findById(2).map(Employee::getName).orElse("không có"));
        System.out.println("Tìm #99: " + repo.findById(99).map(Employee::getName).orElse("không có"));

        System.out.println("\nNhân viên biết Java:");
        repo.findBySkill("Java").forEach(e -> System.out.println("  - " + e.getName()));

        System.out.println("\nGom theo phòng ban (Map<String, List>):");
        repo.groupByDepartment().forEach((dept, list) ->
                System.out.println("  " + dept + ": " + list.stream().map(Employee::getName).toList()));

        System.out.println("\nTổng lương theo phòng:");
        repo.totalSalaryByDepartment().forEach((d, t) -> System.out.printf("  %s: %,.0f%n", d, t));

        System.out.println("\nSắp xếp lương giảm dần:");
        repo.findAllSortedBySalaryDesc().forEach(e ->
                System.out.printf("  %s - %,.0f%n", e.getName(), e.getSalary()));

        System.out.println("\n=== UPDATE ===");
        repo.update(binh.getId(), "Trần Thị Bình", "binh.tran@corp.vn", "HR", 19_500_000);
        System.out.println(repo.findById(binh.getId()).get());
        // Email cũ "binh@corp.vn" đã được giải phóng -> người khác dùng được:
        repo.create("Hoàng Em", "binh@corp.vn", "Sales", 15_000_000);
        System.out.println("Email cũ được tái sử dụng thành công.");

        System.out.println("\n=== DELETE ===");
        System.out.println("Xoá #1: " + repo.delete(1));
        System.out.println("Xoá #1 lần nữa: " + repo.delete(1));
        repo.create("An Mới", "an@corp.vn", "IT", 22_000_000); // email của An đã được giải phóng
        System.out.println("Email của người đã xoá được dùng lại.");

        System.out.println("\n=== AUDIT LOG (List giữ đúng thứ tự, cho phép trùng) ===");
        List<String> log = repo.getAuditLog();
        for (int i = 0; i < log.size(); i++) {
            System.out.printf("%2d. %s%n", i + 1, log.get(i));
        }
    }
}
