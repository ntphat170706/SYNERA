import React from 'react';
import { Platform, View } from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
export function SchedulePicker({
  value,
  onChange,
}: {
  value: Date;
  onChange: (date: Date) => void;
}) {
  return (
    <View>
      <DateTimePicker
        value={value}
        mode={Platform.OS === 'ios' ? 'datetime' : 'date'}
        display={Platform.OS === 'ios' ? 'compact' : 'default'}
        locale="vi-VN"
        onChange={(_, date) => {
          if (date) onChange(date);
        }}
      />
    </View>
  );
}
