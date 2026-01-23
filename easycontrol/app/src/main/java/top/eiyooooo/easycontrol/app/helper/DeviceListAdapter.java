package top.eiyooooo.easycontrol.app.helper;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import top.eiyooooo.easycontrol.app.StartDeviceActivity;
import top.eiyooooo.easycontrol.app.adb.Adb;
import top.eiyooooo.easycontrol.app.client.Client;
import top.eiyooooo.easycontrol.app.entity.AppData;
import top.eiyooooo.easycontrol.app.entity.Device;
import top.eiyooooo.easycontrol.app.R;
import top.eiyooooo.easycontrol.app.databinding.ItemDevicesItemBinding;
import top.eiyooooo.easycontrol.app.databinding.ItemDevicesItemDetailBinding;
import top.eiyooooo.easycontrol.app.databinding.ItemSetDeviceBinding;

public class DeviceListAdapter extends BaseExpandableListAdapter {

  public static final ArrayList<Device> devicesList = new ArrayList<>();
  public static final HashMap<String, UsbDevice> linkDevices = new HashMap<>();
  private final Context context;
  private final ExpandableListView expandableListView;
  public static boolean startedDefault = false;

  // 【新增】记录被折叠的分组名称
  private final Set<String> collapsedGroups = new HashSet<>();

  public DeviceListAdapter(Context c, ExpandableListView expandableListView) {
    this.expandableListView = expandableListView;
    queryDevices();
    context = c;
  }

  @Override
  public int getGroupCount() {
    return devicesList.size();
  }

  @Override
  public int getChildrenCount(int groupPosition) {
    return 1;
  }

  @Override
  public Object getGroup(int groupPosition) {
    return null;
  }

  @Override
  public Object getChild(int groupPosition, int childPosition) {
    return null;
  }

  @Override
  public long getGroupId(int groupPosition) {
    return groupPosition;
  }

  @Override
  public long getChildId(int groupPosition, int childPosition) {
    return childPosition;
  }

  @Override
  public boolean hasStableIds() {
    return false;
  }

  @Override
  public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
    if (convertView == null) {
      ItemDevicesItemBinding devicesItemBinding = ItemDevicesItemBinding.inflate(LayoutInflater.from(context));
      convertView = devicesItemBinding.getRoot();
      convertView.setTag(devicesItemBinding);
    }
    Device device = devicesList.get(groupPosition);
    if (device.connection == -1) checkConnection(device);
    setView(convertView, device, isExpanded, groupPosition);
    return convertView;
  }

  @Override
  public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
    if (convertView == null) {
      ItemDevicesItemDetailBinding devicesItemDetailBinding = ItemDevicesItemDetailBinding.inflate(LayoutInflater.from(context));
      convertView = devicesItemDetailBinding.getRoot();
      convertView.setTag(devicesItemDetailBinding);
    }
    Device device = devicesList.get(groupPosition);
    setChildView(convertView, device);
    return convertView;
  }

  @Override
  public boolean isChildSelectable(int groupPosition, int childPosition) {
    return false;
  }

  private void setView(View view, Device device, boolean isExpanded, int groupPosition) {
    ItemDevicesItemBinding devicesItemBinding = (ItemDevicesItemBinding) view.getTag();
    
    // --- 【修改：分组显示逻辑】 ---
    String currentGroup = device.groupName == null ? "默认分组" : device.groupName;
    boolean isFirstInGroup = false;
    
    if (groupPosition == 0) {
      isFirstInGroup = true;
    } else {
      Device prevDevice = devicesList.get(groupPosition - 1);
      String prevGroup = prevDevice.groupName == null ? "默认分组" : prevDevice.groupName;
      if (!currentGroup.equals(prevGroup)) {
        isFirstInGroup = true;
      }
    }

    if (isFirstInGroup) {
      devicesItemBinding.tvGroupHeader.setVisibility(View.VISIBLE);
      // 根据折叠状态显示不同文字
      String stateIndicator = collapsedGroups.contains(currentGroup) ? " (点击展开 +)" : " (点击收起 -)";
      devicesItemBinding.tvGroupHeader.setText(currentGroup + stateIndicator);
      
      // 点击分组标题切换折叠状态
      devicesItemBinding.tvGroupHeader.setOnClickListener(v -> {
        if (collapsedGroups.contains(currentGroup)) {
          collapsedGroups.remove(currentGroup);
        } else {
          collapsedGroups.add(currentGroup);
        }
        update(); // 触发数据重新过滤
      });
    } else {
      devicesItemBinding.tvGroupHeader.setVisibility(View.GONE);
    }
    // ----------------------------

    devicesItemBinding.deviceExpand.setRotation(isExpanded ? 270 : 180);
    if (device.isLinkDevice()) {
      if (device.connection == 1)
        devicesItemBinding.deviceIcon.setImageResource(R.drawable.link_can_connect);
      else if (device.connection == 0)
        devicesItemBinding.deviceIcon.setImageResource(R.drawable.link_checking_connection);
      else
        devicesItemBinding.deviceIcon.setImageResource(R.drawable.link_can_not_connect);
    }
    else if (device.connection == 0)
      devicesItemBinding.deviceIcon.setImageResource(R.drawable.wifi_checking_connection);
    else if (device.connection == 1)
      devicesItemBinding.deviceIcon.setImageResource(R.drawable.wifi_can_connect);
    else
      devicesItemBinding.deviceIcon.setImageResource(R.drawable.wifi_can_not_connect);
    
    devicesItemBinding.deviceName.setText(device.name);
    
    devicesItemBinding.getRoot().setOnClickListener(v -> {
      if (expandableListView.isGroupExpanded(groupPosition))
        expandableListView.collapseGroup(groupPosition);
      else
        expandableListView.expandGroup(groupPosition);
    });
    
    devicesItemBinding.getRoot().setOnLongClickListener(v -> {
      onLongClickCard(device);
      return true;
    });
  }

  private void setChildView(View view, Device device) {
    ItemDevicesItemDetailBinding devicesItemDetailBinding = (ItemDevicesItemDetailBinding) view.getTag();
    devicesItemDetailBinding.isAudio.setChecked(device.isAudio);
    devicesItemDetailBinding.defaultFull.setChecked(device.defaultFull);
    devicesItemDetailBinding.isAudio.setOnCheckedChangeListener((buttonView, isChecked) -> {
      device.isAudio = isChecked;
      AppData.dbHelper.update(device);
    });
    View isAudioParent = (View) devicesItemDetailBinding.isAudio.getParent();
    isAudioParent.setOnClickListener(v -> devicesItemDetailBinding.isAudio.toggle());
    devicesItemDetailBinding.defaultFull.setOnCheckedChangeListener((buttonView, isChecked) -> {
      device.defaultFull = isChecked;
      AppData.dbHelper.update(device);
    });
    View defaultFullParent = (View) devicesItemDetailBinding.defaultFull.getParent();
    defaultFullParent.setOnClickListener(v -> devicesItemDetailBinding.defaultFull.toggle());
    devicesItemDetailBinding.displayMirroring.setOnClickListener(v -> startDevice(device, 0));
    devicesItemDetailBinding.createDisplay.setOnClickListener(v -> startDevice(device, 1));
  }

  private final Object checkingConnection = new Object();
  private Thread checkingConnectionThread;
  public ExecutorService checkConnectionExecutor;

  private void checkConnection(Device device) {
    device.connection = 0;
    if (checkConnectionExecutor != null && checkConnectionExecutor.isShutdown() && checkingConnectionThread != null) {
      try {
        checkingConnectionThread.join();
      } catch (Exception ignored) {}
    }

    if (checkConnectionExecutor == null)
      checkConnectionExecutor = Executors.newFixedThreadPool(devicesList.size() + 1);

    if (checkingConnectionThread != null) checkingConnectionThread.interrupt();
    checkingConnectionThread = new Thread(() -> {
      try {
        Thread.sleep(1500);
        checkConnectionExecutor.shutdown();
        synchronized (checkingConnection) {
          checkingConnection.notifyAll();
        }
        while (!checkConnectionExecutor.awaitTermination(600, TimeUnit.MILLISECONDS)) {
          checkConnectionExecutor.shutdownNow();
        }
        AppData.uiHandler.post(this::notifyDataSetChanged);
        checkConnectionExecutor = null;
        if (!startedDefault) {
          AppData.uiHandler.post(() -> startDefault(AppData.setting.getTryStartDefaultInAppTransfer() ? 1 : 0));
          startedDefault = true;
        }
      } catch (InterruptedException ignored) {}
    });
    checkingConnectionThread.start();

    checkConnectionExecutor.execute(() -> {
      try {
        if (!Adb.adbMap.containsKey(device.uuid)) {
          if (device.isLinkDevice()) {
            Adb adb = new Adb(device.uuid, linkDevices.get(device.uuid), AppData.keyPair);
            Adb.adbMap.put(device.uuid, adb);
          } else {
            new Adb(device.address, AppData.keyPair);
            Adb adb = new Adb(device.uuid, device.address, AppData.keyPair);
            Adb.adbMap.put(device.uuid, adb);
          }
        }
        synchronized (checkingConnection) {
          checkingConnection.wait();
        }
        if (device.connection == 0) device.connection = 1;
        
        // --- 【修改：删除自动展开代码】 ---
        /*
        if (device.connection == 1) {
            // 已删除自动展开逻辑
        }
        */
        // ------------------------------
        
      } catch (Exception e) {
        device.connection = 2;
        L.log(device.uuid, e);
      }
    });
  }

  private void onLongClickCard(Device device) {
    ItemSetDeviceBinding itemSetDeviceBinding = ItemSetDeviceBinding.inflate(LayoutInflater.from(context));
    Dialog dialog = PublicTools.createDialog(context, true, itemSetDeviceBinding.getRoot());
    if (device.isLinkDevice()) {
      itemSetDeviceBinding.buttonStartWireless.setVisibility(View.VISIBLE);
      itemSetDeviceBinding.buttonStartWireless.setOnClickListener(v -> {
        dialog.cancel();
        UsbDevice usbDevice = linkDevices.get(device.uuid);
        if (usbDevice == null) return;
        Client.restartOnTcpip(device, usbDevice, result -> AppData.uiHandler.post(() -> Toast.makeText(AppData.main, AppData.main.getString(result ? R.string.set_device_button_start_wireless_success : R.string.set_device_button_recover_error), Toast.LENGTH_SHORT).show()));
      });
    } else {
      itemSetDeviceBinding.buttonStartWireless.setVisibility(View.GONE);
    }
    itemSetDeviceBinding.buttonNightMode.setOnClickListener(v -> {
      dialog.cancel();
      PublicTools.showNightModeChanger(context, device);
    });
    itemSetDeviceBinding.buttonGetUuid.setOnClickListener(v -> {
      dialog.cancel();
      AppData.clipBoard.setPrimaryClip(ClipData.newPlainText(MIMETYPE_TEXT_PLAIN, device.uuid));
      Toast.makeText(AppData.main, AppData.main.getString(R.string.set_device_button_get_uuid_success), Toast.LENGTH_SHORT).show();
    });
    itemSetDeviceBinding.buttonCreateShortcut.setOnClickListener(v -> {
        try {
          if (device.specified_app == null || device.specified_app.isEmpty()) throw new Exception();
          ShortcutHelper.addShortcut(AppData.main, StartDeviceActivity.class, device.name, Adb.getRemoteIconByDevice(device, device.specified_app), device.uuid);
        } catch (Exception e) {
          L.log(device.uuid, e);
          ShortcutHelper.addShortcut(AppData.main, StartDeviceActivity.class, device.name, R.drawable.phone, device.uuid);
        }
    });
    itemSetDeviceBinding.buttonChange.setOnClickListener(v -> {
      dialog.cancel();
      PublicTools.createAddDeviceView(context, device, this).show();
    });
    itemSetDeviceBinding.buttonDelete.setOnClickListener(v -> {
      AppData.dbHelper.delete(device);
      if (Adb.adbMap.containsKey(device.uuid)) {
        Objects.requireNonNull(Adb.adbMap.get(device.uuid)).close();
      }
      update();
      dialog.cancel();
    });
    dialog.show();
  }

  private void queryDevices() {
    ArrayList<Device> rawDevices = AppData.dbHelper.getAll();
    
    // 1. 按分组名称排序
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
      rawDevices.sort((o1, o2) -> {
        String g1 = o1.groupName == null ? "默认分组" : o1.groupName;
        String g2 = o2.groupName == null ? "默认分组" : o2.groupName;
        return g1.compareTo(g2);
      });
    }

    devicesList.clear();
    String lastGroup = null;

    // 2. 数据展平与过滤
    for (Device device : rawDevices) {
      String currentGroup = device.groupName == null ? "默认分组" : device.groupName;
      
      // 每组的第一个设备必须添加，因为它承载了 tvGroupHeader 的显示
      if (lastGroup == null || !currentGroup.equals(lastGroup)) {
        devicesList.add(device);
        lastGroup = currentGroup;
      } 
      // 如果该组没有被标记为折叠，则添加该组后续的设备
      else if (!collapsedGroups.contains(currentGroup)) {
        devicesList.add(device);
      }
    }
    
    if (!startedDefault && devicesList.isEmpty()) startedDefault = true;
  }

  public static void startByUUID(String uuid, int mode) {
    for (Device device : devicesList) {
      if (Objects.equals(device.uuid, uuid)) startDevice(device, mode);
    }
  }

  public static void startDevice(Device device, int mode) {
    if (device.isLinkDevice()) {
      UsbDevice usbDevice = linkDevices.get(device.uuid);
      if (usbDevice == null) return;
      new Client(device, usbDevice, mode);
    } else new Client(device, null, mode);
  }

  public static void startDefault(int mode) {
    boolean started = false;
    for (Device device : devicesList) {
      if (device.connectOnStart) {
        startDevice(device, mode);
        started = true;
        if (AppData.setting.getAlwaysFullMode()) break;
      }
    }
    if (started && !AppData.setting.getAlwaysFullMode() && AppData.setting.getAutoBackOnStartDefault()) {
      Intent home = new Intent(Intent.ACTION_MAIN);
      home.addCategory(Intent.CATEGORY_HOME);
      AppData.activity.startActivity(home);
    }
  }

  public final void update() {
    for (int i = 0; i < devicesList.size(); i++)
      expandableListView.collapseGroup(i);
    queryDevices();
    notifyDataSetChanged();
  }
}
